#ifndef BLCKAUTHZ_H
#define BLCKAUTHZ_H
/* BLCKAUTHZ 42: authorization only, not authentication or crypto.
 * Validated sessions MUST come from the trusted gateway. */
#include <stddef.h>
#include <stdint.h>
#define BLCKAUTHZ_NAME_MAX 64u
typedef enum {
    BLCK_ROLE_NONE=0, BLCK_ROLE_INVITED=1,
    BLCK_ROLE_MEMBER=2, BLCK_ROLE_ADMIN=3
} blck_role;
typedef enum {
    BLCK_ACT_READ=1, BLCK_ACT_POST=2, BLCK_ACT_INVITE=3,
    BLCK_ACT_REMOVE=4, BLCK_ACT_MANAGE_ROOM=5
} blck_action;
typedef struct {
    char room[BLCKAUTHZ_NAME_MAX + 1u];
    char subject[BLCKAUTHZ_NAME_MAX + 1u];
    uint64_t expires_at;
    blck_role role;
    unsigned verified;
    unsigned revoked;
} blck_session;
int blck_authorized(const blck_session *session, const char *room,
                    blck_action action, uint64_t now);
#endif
