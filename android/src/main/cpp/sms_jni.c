#include <jni.h>
#include "blcksms_frame.h"
#include <string.h>
#include <stdint.h>

/* JNI is only a platform adapter. The framing/parser logic is ISO C11. */
JNIEXPORT jstring JNICALL
Java_com_blckswan_blckchat_NativeSmsCodec_pack(
    JNIEnv *env, jclass cls, jbyteArray already_sealed, jstring msgid) {
    char parts[BLCKSMS_PARTS_MAX][BLCKSMS_FRAME_TEXT];
    char combined[BLCKSMS_PARTS_MAX*(BLCKSMS_FRAME_TEXT+1u)];
    size_t count=0u,offset=0u,i;
    const char *id;
    jbyte *data;
    jsize length;
    jstring result;
    (void)cls;
    if (!already_sealed || !msgid) return NULL;
    length=(*env)->GetArrayLength(env,already_sealed);
    if (length<=0 || length>(jsize)BLCKSMS_SEALED_MAX) return NULL;
    id=(*env)->GetStringUTFChars(env,msgid,NULL);
    if (!id) return NULL;
    data=(*env)->GetByteArrayElements(env,already_sealed,NULL);
    if (!data) {
        (*env)->ReleaseStringUTFChars(env,msgid,id);
        return NULL;
    }
    if (blcksms_frame_pack((const uint8_t *)data,(size_t)length,
                          id,parts,BLCKSMS_PARTS_MAX,&count)!=0) {
        (*env)->ReleaseByteArrayElements(env,already_sealed,data,JNI_ABORT);
        (*env)->ReleaseStringUTFChars(env,msgid,id);
        return NULL;
    }
    (*env)->ReleaseByteArrayElements(env,already_sealed,data,JNI_ABORT);
    (*env)->ReleaseStringUTFChars(env,msgid,id);
    for(i=0u;i<count;i++){
        size_t n=strlen(parts[i]);
        if(offset+n+2u>sizeof(combined))return NULL;
        memcpy(combined+offset,parts[i],n);
        offset+=n;
        if(i+1u<count)combined[offset++]='\n';
    }
    combined[offset]='\0';
    result=(*env)->NewStringUTF(env,combined);
    memset(combined,0,sizeof combined);
    memset(parts,0,sizeof parts);
    return result;
}

JNIEXPORT jbyteArray JNICALL
Java_com_blckswan_blckchat_NativeSmsCodec_unpack(
    JNIEnv *env,jclass cls,jstring message) {
    blcksms_frame_collector state;
    uint8_t sealed[BLCKSMS_SEALED_MAX];
    size_t decoded=0u,seen=0u;
    const char *raw;
    size_t n,start=0u,i;
    blcksms_frame_result status=BLCKSMS_FRAME_PENDING;
    jbyteArray result=NULL;
    (void)cls;
    if(!message)return NULL;
    raw=(*env)->GetStringUTFChars(env,message,NULL);
    if(!raw)return NULL;
    n=strlen(raw);
    if(!n||n>BLCKSMS_PARTS_MAX*(BLCKSMS_FRAME_TEXT+1u)){
        (*env)->ReleaseStringUTFChars(env,message,raw);
        return NULL;
    }
    blcksms_frame_reset(&state);
    for(i=0u;i<=n;i++){
        if(i==n || raw[i]=='\n'){
            char frame[BLCKSMS_FRAME_TEXT];
            size_t frame_len=i-start;
            if(frame_len && raw[start+frame_len-1u]=='\r') frame_len--;
            if(!frame_len || frame_len>=sizeof frame ||
               seen>=BLCKSMS_PARTS_MAX || status==BLCKSMS_FRAME_COMPLETE)
                goto done;
            memcpy(frame,raw+start,frame_len);
            frame[frame_len]='\0';
            status=blcksms_frame_receive(&state,frame,sealed,
                                         sizeof sealed,&decoded);
            seen++;
            if(status==BLCKSMS_FRAME_ERROR)goto done;
            start=i+1u;
        } else if((unsigned char)raw[i]>=128u) {
            goto done;
        }
    }
    if(status!=BLCKSMS_FRAME_COMPLETE || decoded==0u)goto done;
    result=(*env)->NewByteArray(env,(jsize)decoded);
    if(result)(*env)->SetByteArrayRegion(env,result,0,(jsize)decoded,
                                        (const jbyte *)sealed);
done:
    memset(&state,0,sizeof state);
    memset(sealed,0,sizeof sealed);
    (*env)->ReleaseStringUTFChars(env,message,raw);
    return result;
}
