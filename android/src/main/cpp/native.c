#include <jni.h>
#include "blckui.h"
#include <stddef.h>
#include <stdint.h>

/* C11-owned design tokens, Android Java is only a platform adapter. */
JNIEXPORT jint JNICALL
Java_com_blckswan_blckchat_MainActivity_nativeAccent(JNIEnv *env, jclass cls) {
    bsui_theme t=bsui_theme_dark();
    (void)env; (void)cls;
    return (jint)(0xff000000u | ((uint32_t)t.accent.r<<16u) |
                  ((uint32_t)t.accent.g<<8u) | (uint32_t)t.accent.b);
}

JNIEXPORT jboolean JNICALL
Java_com_blckswan_blckchat_MainActivity_nativeValidateMessage(
    JNIEnv *env, jclass cls, jstring text) {
    const char *s;
    size_t i, len;
    (void)cls;
    if (!text) return JNI_FALSE;
    s=(*env)->GetStringUTFChars(env,text,NULL);
    if (!s) return JNI_FALSE;
    len=0u;
    while (s[len] && len<=2048u) len++;
    if (!len || len>2048u) {
        (*env)->ReleaseStringUTFChars(env,text,s);
        return JNI_FALSE;
    }
    for (i=0u;i<len;i++) {
        unsigned char c=(unsigned char)s[i];
        if (c<32u && c!=9u && c!=10u) {
            (*env)->ReleaseStringUTFChars(env,text,s);
            return JNI_FALSE;
        }
    }
    (*env)->ReleaseStringUTFChars(env,text,s);
    return JNI_TRUE;
}
