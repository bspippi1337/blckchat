#include "blcksecure.h"
#include <limits.h>
#include <openssl/rand.h>
#include <openssl/hmac.h>
#include <openssl/crypto.h>
#include <openssl/evp.h>
/* OpenSSL is restricted to this adapter; core headers remain ISO C11. */
int blck_secure_random(uint8_t *output, size_t count) {
    if (!output || count==0u || count>INT_MAX) return -1;
    return RAND_bytes(output,(int)count)==1 ? 0 : -1;
}
int blck_secure_mac(const uint8_t key[32],const void *input,
                    size_t count,uint8_t output[32]) {
    unsigned n=0u;
    if (!key || !input || !output || count>INT_MAX) return -1;
    if (!HMAC(EVP_sha256(),key,32,(const unsigned char *)input,
              count,output,&n)) return -1;
    return n==32u ? 0 : -1;
}
int blck_secure_equal(const uint8_t *a,const uint8_t *b,size_t n) {
    if (!a || !b || n==0u || n>INT_MAX) return 0;
    return CRYPTO_memcmp(a,b,n)==0;
}
void blck_secure_zero(void *p,size_t n) {
    if (p && n) OPENSSL_cleanse(p,n);
}
