#include "blckauthz.h"
#include <string.h>
static int valid_name(const char *s) {
    size_t n=0;
    if (!s) return 0;
    while (n<=BLCKAUTHZ_NAME_MAX && s[n]!='\0') ++n;
    return n>0 && n<=BLCKAUTHZ_NAME_MAX;
}
int blck_authorized(const blck_session *s, const char *room,
                    blck_action action, uint64_t now) {
    if (!s || !valid_name(room) || !valid_name(s->room) ||
        !valid_name(s->subject) || !s->verified || s->revoked ||
        now>=s->expires_at || strcmp(room,s->room)!=0) return 0;
    if (s->role==BLCK_ROLE_ADMIN) {
        switch (action) {
        case BLCK_ACT_READ: case BLCK_ACT_POST: case BLCK_ACT_INVITE:
        case BLCK_ACT_REMOVE: case BLCK_ACT_MANAGE_ROOM: return 1;
        default: return 0;
        }
    }
    if (s->role==BLCK_ROLE_MEMBER)
        return action==BLCK_ACT_READ || action==BLCK_ACT_POST;
    return 0;
}
