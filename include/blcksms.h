#ifndef BLCKSMS_H
#define BLCKSMS_H
/* BLCKSMS 42: portable C11 SMS message formatting.
 * This library never accesses the modem or network.
 * No cryptography is implemented here.
 */
#include <stddef.h>

#define BLCKSMS_MAX_PHONE 17u
#define BLCKSMS_MAX_TEXT 384u
#define BLCKSMS_INVITE_ID_MAX 64u

typedef enum {
    BLCKSMS_OK = 0,
    BLCKSMS_INVALID = -1,
    BLCKSMS_NO_SPACE = -2
} blcksms_result;

typedef struct {
    char recipient[BLCKSMS_MAX_PHONE];
    char body[BLCKSMS_MAX_TEXT];
} blcksms_outgoing;

/* Expected: + followed by 8..15 ASCII digits, first digit nonzero.
 * This validates syntax, not whether the number is assigned. */
int blcksms_valid_e164(const char *phone);

/* invite_id is an unprivileged public locator, not an authentication token.
 * The session still requires a new server-issued OTP after installation.
 * Returns error if input/size invalid; never truncates.
 */
blcksms_result blcksms_invitation(
    blcksms_outgoing *out, const char *phone, const char *invite_id);

/* OTP must come from the authentication gateway, not client-generated.
 * The gateway hashes, expires, rate-limits and atomically redeems it.
 */
blcksms_result blcksms_login_code(
    blcksms_outgoing *out, const char *phone, const char *eight_digit_code);
#endif
