package org.apache.kafka.common.network;

import org.apache.kafka.common.memory.MemoryPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SharedMemoryReceiveTest {

    private static final String SOURCE = "test-source";
    private MappedByteBuffer sharedBuffer;
    private SharedMemoryReceive receive;
    private static final int MAX_SIZE = 128;

    @BeforeEach
    void setUp() {
        sharedBuffer = Mockito.mock(MappedByteBuffer.class);
        receive = new SharedMemoryReceive(MAX_SIZE, SOURCE, MemoryPool.NONE, sharedBuffer);
    }

    @Test
    void testReadFromSharedMemory_ValidSize() throws IOException {
        // Arrange
        when(sharedBuffer.remaining()).thenReturn(4, 128);
        when(sharedBuffer.get()).thenReturn((byte) 0, (byte) 0, (byte) 0, (byte) 64); // Size = 64

        // Act
        long bytesRead = receive.readFromSharedMemory();

        // Assert
        assertEquals(4, bytesRead, "Should read 4 bytes for the size.");
        assertTrue(receive.requiredMemoryAmountKnown(), "Memory amount should be known after reading size.");
        assertEquals(64, receive.requestedBufferSize, "Requested buffer size should match the read size.");
    }
}