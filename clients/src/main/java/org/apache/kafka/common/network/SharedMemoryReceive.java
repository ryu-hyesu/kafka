package org.apache.kafka.common.network;

import org.apache.kafka.common.memory.MemoryPool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ScatteringByteChannel;

public class SharedMemoryReceive implements Receive {
    public static final String UNKNOWN_SOURCE = "";
    public static final int UNLIMITED = -1;
    private static final Logger log = LoggerFactory.getLogger(SharedMemoryReceive.class);
    private static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocate(0);

    private final String source;
    private final ByteBuffer size;
    private final int maxSize;
    private final MemoryPool memoryPool;
    private int requestedBufferSize = -1;
    private ByteBuffer buffer;
    private final MappedByteBuffer sharedBuffer; // 공유 메모리
    private final ReentrantLock lock;
    private volatile long readPosition = 0;
    private volatile long writePosition = 0;
    
    public SharedMemoryReceive(String source, MappedByteBuffer sharedBuffer) {
        this(UNLIMITED, source, MemoryPool.NONE, sharedBuffer);
    }

    public SharedMemoryReceive(int maxSize, String source, MemoryPool memoryPool, MappedByteBuffer sharedBuffer) {
        this.source = source;
        this.size = ByteBuffer.allocate(4);
        this.buffer = null;
        this.maxSize = maxSize;
        this.memoryPool = memoryPool;
        this.sharedBuffer = sharedBuffer;
        this.lock = new ReentrantLock();
    }

    @Override
    public String source() {
        return source;
    }

    @Override
    public boolean complete() {
        return !size.hasRemaining() && buffer != null && !buffer.hasRemaining();
    }

    /**
     * Reads data from shared memory buffer
     * Returns the number of bytes read
     */
    public long readFromSharedMemory() throws IOException {
        int read = 0;
        lock.lock(); // 동기화를 위한 락 획득
        try {
            // 읽을 메시지가 남아있는 경우
            if (size.hasRemaining()) {
                while (size.hasRemaining() && sharedBuffer.remaining() >= 4) {
                    size.put(sharedBuffer.get()); // 1바이트씩 읽어 size 버퍼에 저장
                    read++;
                }
                
                if (!size.hasRemaining()) {
                    size.rewind(); // size 버퍼의 읽기 위치 초기화
                    int receiveSize = size.getInt(); // 읽은 데이터를 정수로 변환해 메시지 크가를 반환
                    if (receiveSize < 0)
                        throw new InvalidReceiveException("Invalid receive (size = " + receiveSize + ")");
                    if (maxSize != UNLIMITED && receiveSize > maxSize)
                        throw new InvalidReceiveException("Invalid receive (size = " + receiveSize + 
                            " larger than " + maxSize + ")");
                    
                    requestedBufferSize = receiveSize; // 메시지 크기 저장
                    if (receiveSize == 0) {
                        buffer = EMPTY_BUFFER; // 메시지 = 0, 빈 버퍼 할당
                    }
                }
            }

            // 버퍼가 없고 메시지 크기가 설정된 경우 실행
            if (buffer == null && requestedBufferSize != -1) {
                buffer = memoryPool.tryAllocate(requestedBufferSize); // 요청한 크기만큼 할당
                if (buffer == null) {
                    log.trace("Low on memory - could not allocate buffer of size {} for source {}", 
                        requestedBufferSize, source);
                    return read; // 할당 실패 시 로그 및 남은 바이트 수 반환
                }
            }

            // 메시지 데이터 읽기
            if (buffer != null && buffer.hasRemaining() && sharedBuffer.remaining() > 0) { // 버퍼 초기화 & 버퍼에 여유 공간 & 공유 메모리에 읽을 데이터가 존재
                int bytesToRead = Math.min(buffer.remaining(), sharedBuffer.remaining()); // 읽기 크기 계산 (buffer의 남은 공간과 공유 메모리의 크기와 비교)
                byte[] temp = new byte[bytesToRead]; // 임시 배열에 데이터를 저장
                sharedBuffer.get(temp); 
                buffer.put(temp);
                read += bytesToRead;
                
                // Update read position
                readPosition += bytesToRead;
            }

            return read; // 읽은 바이트 수
        } finally {
            lock.unlock(); // 락 해제
        }
    }

    @Override
    public boolean requiredMemoryAmountKnown() {
        return requestedBufferSize != -1;
    }

    @Override
    public boolean memoryAllocated() {
        return buffer != null;
    }

    @Override
    public void close() throws IOException {
        if (buffer != null && buffer != EMPTY_BUFFER) {
            memoryPool.release(buffer);
            buffer = null;
        }
    }

    public ByteBuffer payload() {
        if (buffer != null) {
            buffer.flip();
            return buffer;
        }
        return null;
    }

    public int bytesRead() {
        if (buffer == null)
            return size.position();
        return buffer.position() + size.position();
    }

    public int size() {
        return payload().limit() + size.limit();
    }

    /**
     * Get the current read position in shared memory
     */
    public long getReadPosition() {
        return readPosition;
    }

    /**
     * Get the current write position in shared memory
     */
    public long getWritePosition() {
        return writePosition;
    }

    /**
     * Update the write position when new data is written to shared memory
     */
    public void updateWritePosition(long newPosition) {
        this.writePosition = newPosition;
    }

    /**
     * Check if there is data available to read
     */
    public boolean hasDataToRead() {
        return writePosition > readPosition;
    }

    /**
     * Get the amount of data available to read
     */
    public long availableData() {
        return writePosition - readPosition;
    }

    /**
     * Reset positions when buffer is cleared or reused
     */
    public void reset() {
        readPosition = 0;
        writePosition = 0;
        size.clear();
        if (buffer != null) {
            buffer.clear();
        }
    }
}