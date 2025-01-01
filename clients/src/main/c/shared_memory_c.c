#include <jni.h>
#include <windows.h>
#include <stdio.h>
#include <stdint.h>
#include <string.h>
#include "org_apache_kafka_clients_consumer_SharedMemoryManager.h"

// Shared memory structure
#define BUF_COUNT 10
#define BUF_SIZE 256
#define SHARED_MEMORY_NAME "Local\\KafkaSharedMemoryc"
#define EVENT_NAME "Local\\KafkaEventc"

typedef struct {
    char data[BUF_COUNT][BUF_SIZE];  // Shared memory data buffer
    uint64_t prod_seq;
    uint64_t cons_seq;
    HANDLE event_handle;  // Event handle for Windows
} shm_mem_t;

// Global shared memory pointer
shm_mem_t *shm_base = NULL; // 공유 메모리의 시작 주소
HANDLE shm_handle = NULL; // 공유 메모리 파일 핸들
HANDLE waitHandle = NULL; // RegisterWait 핸들

int initialize_shared_memory() {
    // Create or open shared memory
    shm_handle = CreateFileMapping(
        INVALID_HANDLE_VALUE,
        NULL,
        PAGE_READWRITE,
        0,
        sizeof(shm_mem_t),
        SHARED_MEMORY_NAME
    );

    if (!shm_handle) {
        printf("CreateFileMapping failed: %d\n", GetLastError());
        return -1;
    }

    // 공유 메모리를 프로세스 주소 공간에 매핑
    shm_base = (shm_mem_t *)MapViewOfFile(
        shm_handle,
        FILE_MAP_ALL_ACCESS,
        0,
        0,
        sizeof(shm_mem_t)
    );

    if (!shm_base) {
        printf("MapViewOfFile failed: %d\n", GetLastError());
        CloseHandle(shm_handle);
        return -1;
    }

    // 이벤트 헨들 열다
    shm_base->event_handle = OpenEvent(EVENT_ALL_ACCESS, FALSE, EVENT_NAME);
    if (!shm_base->event_handle) {
        shm_base->event_handle = CreateEvent(NULL, FALSE, FALSE, EVENT_NAME);
        if (!shm_base->event_handle) {
            printf("CreateEvent failed: %d\n", GetLastError());
            UnmapViewOfFile(shm_base);
            CloseHandle(shm_handle);
            return -1;
        }
    }

    // Initialize sequence numbers if new segment
    if (GetLastError() != ERROR_ALREADY_EXISTS) {
        shm_base->prod_seq = 0;
        shm_base->cons_seq = 0;
    }

    return 0;
}

// 이벤트
void cleanup_shared_memory() {
    if (shm_base) {
        UnmapViewOfFile(shm_base);
    }
    if (shm_handle) {
        CloseHandle(shm_handle);
    }
    if (shm_base->event_handle) {
        CloseHandle(shm_base->event_handle);
    }
}

JNIEXPORT void JNICALL Java_org_apache_kafka_clients_consumer_SharedMemoryManager_writeSharedMemory
(JNIEnv *env, jobject obj, jstring content) {
    const char *nativeContent = (*env)->GetStringUTFChars(env, content, NULL);

    if (!nativeContent) {
        printf("Failed to convert jstring to C string.\n");
        return;
    }

    if (!shm_base) {
        if (initialize_shared_memory() != 0) {
            printf("Failed to initialize shared memory.\n");
            (*env)->ReleaseStringUTFChars(env, content, nativeContent);
            return;
        }
    }

    uint64_t cons_seq = shm_base->cons_seq;

    // Check for buffer overflow
    if ((shm_base->prod_seq - cons_seq) >= BUF_COUNT) {
        printf("Buffer is full, cannot write.\n");
        (*env)->ReleaseStringUTFChars(env, content, nativeContent);
        return;
    }

    // Write data to shared memory
    snprintf(shm_base->data[shm_base->prod_seq % BUF_COUNT], BUF_SIZE, "%s", nativeContent);
    shm_base->prod_seq++;

    // 컨슈머에 데이터 쓰기 신호 전달
    if (!SetEvent(shm_base->event_handle)) {
        printf("Failed to signal event: %d\n", GetLastError());
    }

    (*env)->ReleaseStringUTFChars(env, content, nativeContent);
}

JNIEXPORT jstring JNICALL Java_org_apache_kafka_clients_consumer_SharedMemoryManager_readSharedMemory
(JNIEnv *env, jobject obj) {
    if (!shm_base) {
        if (initialize_shared_memory() != 0) {
            printf("Failed to initialize shared memory.\n");
            return NULL;
        }
    }

    // Check if there is new data to read
    if (shm_base->cons_seq >= shm_base->prod_seq) {
        return NULL; // No data available
    }

    // Read the next data from shared memory
    const char *data = shm_base->data[shm_base->cons_seq % BUF_COUNT];
    shm_base->cons_seq++; // Increment the consumer sequence

    // Convert the C string to a Java string and return it
    return (*env)->NewStringUTF(env, data);
}
