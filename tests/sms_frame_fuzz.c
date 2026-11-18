/*
 * libFuzzer/ASan/UBSan harness for the pure C11 encrypted-SMS frame parser.
 * No secret material is required: the input is opaque ciphertext bytes.
 *
 * Build (Linux clang):
 * clang -O1 -g -std=c11 -fsanitize=fuzzer,address,undefined \
 *   -fno-omit-frame-pointer -Iinclude \
 *   tests/sms_frame_fuzz.c src/blcksms_frame.c -o /tmp/blcksms_fuzz
 * /tmp/blcksms_fuzz -runs=10000 -max_len=4000
 */
#include "blcksms_frame.h"
#include <stdint.h>
#include <stddef.h>
#include <string.h>
#include <stdlib.h>

static const char message_id[]="0123456789abcdef0123456789abcdef";
static void test_valid(const uint8_t *data,size_t size) {
    char frames[BLCKSMS_PARTS_MAX][BLCKSMS_FRAME_TEXT];
    uint8_t result[BLCKSMS_SEALED_MAX];
    blcksms_frame_collector collector;
    size_t parts=0u,n=0u,i;
    if(size==0u || size>BLCKSMS_SEALED_MAX)return;
    if(blcksms_frame_pack(data,size,message_id,frames,
                          BLCKSMS_PARTS_MAX,&parts)!=0)abort();
    blcksms_frame_reset(&collector);
    for(i=parts;i>0u;i--) {
        blcksms_frame_result status=blcksms_frame_receive(
            &collector,frames[i-1u],result,sizeof result,&n);
        if(i>1u && status!=BLCKSMS_FRAME_PENDING)abort();
        if(i==1u && status!=BLCKSMS_FRAME_COMPLETE)abort();
    }
    if(n!=size || memcmp(result,data,size)!=0)abort();
}

int LLVMFuzzerTestOneInput(const uint8_t *data,size_t size) {
    char frame[BLCKSMS_FRAME_TEXT];
    uint8_t buffer[BLCKSMS_SEALED_MAX];
    blcksms_frame_collector collector;
    size_t decoded=0u,n;
    if(!data || size==0u)return 0;

    /* Arbitrary input interpreted as an adversarial SMS frame. */
    n=size<BLCKSMS_FRAME_LIMIT?size:BLCKSMS_FRAME_LIMIT;
    memcpy(frame,data,n);
    frame[n]='\0';
    blcksms_frame_reset(&collector);
    (void)blcksms_frame_receive(&collector,frame,buffer,
                                sizeof buffer,&decoded);
    /* Invalid and valid Unicode-unaware bytes must never cause memory UBs.
       A parser rejection is normal; this fuzz target checks safety only. */

    /* Valid opaque ciphertext must always round-trip in reverse SMS order. */
    test_valid(data,size);
    return 0;
}
