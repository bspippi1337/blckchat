#ifndef BLCKROOM_H
#define BLCKROOM_H
#include <stddef.h>
#include <stdint.h>
/* BLCKROOM 42: RAM-only gateway domain engine, NOT a deployed messenger.
 * The gateway must serialize access, provide atomic persistence,
 * authenticate admins and transmit OTPs via its trusted SMS sender.
 * Public header is ISO C11 only. */
#define BLCK_MAX_INVITES 32u
#define BLCK_MAX_SESSIONS 48u
#define BLCK_MAX_MESSAGES 256u
#define BLCK_MAX_TEXT 1024u
#define BLCK_TOKEN_HEX 65u
#define BLCK_INVITE_HEX 33u
#define BLCK_OTP_TEXT 9u
#define BLCK_SESSION_TTL 86400ull
#define BLCK_INVITE_TTL 604800ull
#define BLCK_OTP_TTL 600ull
/* Gateway must add rate limits by phone, IP, device and room. */
#define BLCK_OTP_COOLDOWN 60ull
#define BLCK_OTP_MAX_ISSUES 3u
#define BLCK_OTP_MAX_GUESSES 5u
typedef enum {
    BLCK_OK=0, BLCK_BAD_INPUT=-1, BLCK_UNAUTHORIZED=-2,
    BLCK_EXPIRED=-3, BLCK_LIMIT=-4, BLCK_FULL=-5, BLCK_INTERNAL=-6
} blck_status;
typedef struct {
    char locator[BLCK_INVITE_HEX], phone[17];
    uint8_t otp_digest[32];
    uint64_t invite_expires,otp_expires,last_issued;
    unsigned failed, issues;
    int live,redeemed,revoked,otp_active;
} blck_invite;
typedef struct {
    uint8_t digest[32];
    char display_name[33];
    uint64_t expires, visible_after;
    size_t invite_index;
    int active,admin;
} blck_session;
typedef struct {
    uint64_t sequence,timestamp;
    char author[33];
    char text[BLCK_MAX_TEXT+1u];
} blck_message;
typedef struct {
    char room[49];
    uint8_t secret[32];
    blck_invite invites[BLCK_MAX_INVITES];
    blck_session sessions[BLCK_MAX_SESSIONS];
    blck_message messages[BLCK_MAX_MESSAGES];
    size_t message_count;
    uint64_t next_sequence;
    int initialized;
} blck_room;
/* issue_otp() is an INTERNAL call and MUST NEVER be exposed over an
 * unauthenticated endpoint: its plaintext result is only for the
 * trusted admin-phone SMS job. Caller must wipe tokens after use. */
blck_status blck_room_init(blck_room *room,const char *room_name,
                           const char *admin_name,uint64_t now,
                           char admin_token[BLCK_TOKEN_HEX]);
blck_status blck_room_invite(blck_room *room,const char *admin_token,
                             const char *phone,uint64_t now,
                             char locator[BLCK_INVITE_HEX]);
blck_status blck_room_issue_otp(blck_room *room,const char *locator,
                                uint64_t now,char otp[BLCK_OTP_TEXT]);
blck_status blck_room_join(blck_room *room,const char *locator,
                           const char *otp,const char *display_name,
                           uint64_t now,char guest_token[BLCK_TOKEN_HEX]);
blck_status blck_room_post(blck_room *room,const char *session_token,
                           const char *text,uint64_t now,uint64_t *sequence);
blck_status blck_room_read(blck_room *room,const char *session_token,
                           uint64_t since,uint64_t now,blck_message *out,
                           size_t capacity,size_t *count);
blck_status blck_room_revoke(blck_room *room,const char *admin_token,
                             const char *locator,uint64_t now);
void blck_room_clear(blck_room *room);
#endif
