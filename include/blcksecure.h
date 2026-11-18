#ifndef BLCKSECURE_H
#define BLCKSECURE_H
#include <stddef.h>
#include <stdint.h>
/* Stable C11 facade. Cryptographic primitives live in a reviewed provider. */
int blck_secure_random(uint8_t *output, size_t count);
int blck_secure_mac(const uint8_t key[32], const void *input,
                    size_t count, uint8_t output[32]);
int blck_secure_equal(const uint8_t *a, const uint8_t *b, size_t count);
void blck_secure_zero(void *p, size_t count);
#endif
