#ifndef BLCKSMS_FRAME_H
#define BLCKSMS_FRAME_H
/*
 * BLCKCHAT SMS framing protocol v1. Pure ISO C11, no network or crypto.
 *
 * Accept only opaque, ALREADY ENCRYPTED AND AUTHENTICATED ciphertext.
 * This module does not implement encryption, authentication, key exchange
 * or replay protection across app restarts.
 * 1-based SMS part index; strict ASCII/base64url, <= 153 characters.
 */
#include <stddef.h>
#include <stdint.h>
#define BLCKSMS_FRAME_LIMIT 153u
#define BLCKSMS_FRAME_TEXT 154u
#define BLCKSMS_PART_BYTES 96u
#define BLCKSMS_PARTS_MAX 24u
#define BLCKSMS_SEALED_MAX 1536u
#define BLCKSMS_ID_TEXT 33u
typedef enum {
    BLCKSMS_FRAME_ERROR=-1,
    BLCKSMS_FRAME_PENDING=1,
    BLCKSMS_FRAME_COMPLETE=2
} blcksms_frame_result;

typedef struct {
    char id[BLCKSMS_ID_TEXT];
    char chunks[BLCKSMS_PARTS_MAX][BLCKSMS_PART_BYTES+1u];
    unsigned char sizes[BLCKSMS_PARTS_MAX];
    unsigned char received[BLCKSMS_PARTS_MAX];
    unsigned count;
    unsigned stored;
    unsigned complete;
} blcksms_frame_collector;

/* Ciphertext bytes MUST already be AEAD sealed using a reviewed crypto
 * provider. Caller supplies cryptographically random 128-bit message ID
 * in 32 lowercase hexadecimal characters. Returns number of frames.
 */
int blcksms_frame_pack(const uint8_t *sealed, size_t sealed_len,
                       const char id[BLCKSMS_ID_TEXT],
                       char out[][BLCKSMS_FRAME_TEXT],
                       size_t available, size_t *frame_count);
void blcksms_frame_reset(blcksms_frame_collector *collector);
/* One collector tracks one ID. Distinct message IDs need separate
 * collectors; the caller must bound active collectors and implement TTL.
 * Return PENDING, COMPLETE or ERROR. On COMPLETE, decoded sealed bytes
 * are copied to out, then the caller verifies AEAD before rendering.
 */
blcksms_frame_result blcksms_frame_receive(
    blcksms_frame_collector *collector, const char *frame,
    uint8_t *out, size_t available, size_t *sealed_len);
#endif
