/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class org_apache_kafka_clients_consumer_SharedMemoryManager */

#ifndef _Included_org_apache_kafka_clients_consumer_SharedMemoryManager
#define _Included_org_apache_kafka_clients_consumer_SharedMemoryManager
#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     org_apache_kafka_clients_consumer_SharedMemoryManager
 * Method:    writeSharedMemory
 * Signature: (Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_org_apache_kafka_clients_consumer_SharedMemoryManager_writeSharedMemory
  (JNIEnv *, jclass, jstring);

/*
 * Class:     org_apache_kafka_clients_consumer_SharedMemoryManager
 * Method:    readSharedMemory
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_org_apache_kafka_clients_consumer_SharedMemoryManager_readSharedMemory
  (JNIEnv *, jclass);

#ifdef __cplusplus
}
#endif
#endif
