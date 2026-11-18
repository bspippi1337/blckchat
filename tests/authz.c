#include "blckauthz.h"
#include <assert.h>
#include <stdio.h>
#include <string.h>
static blck_authz_session session(const char *name, blck_role role) {
    blck_authz_session s={0};
    (void)strcpy(s.room,"workshop"); (void)strcpy(s.subject,name);
    s.role=role; s.expires_at=300; s.verified=1; return s;
}
int main(void) {
    blck_authz_session phoneA=session("PhoneA",BLCK_ROLE_ADMIN);
    blck_authz_session phoneB=session("PhoneB",BLCK_ROLE_MEMBER);
    blck_authz_session phoneC=session("PhoneC",BLCK_ROLE_MEMBER);
    blck_authz_session guest=session("NewGuest",BLCK_ROLE_INVITED);
    assert(blck_authorized(&phoneA,"workshop",BLCK_ACT_INVITE,200));
    assert(blck_authorized(&phoneA,"workshop",BLCK_ACT_POST,200));
    assert(blck_authorized(&phoneB,"workshop",BLCK_ACT_POST,200));
    assert(blck_authorized(&phoneC,"workshop",BLCK_ACT_READ,200));
    assert(!blck_authorized(&phoneB,"workshop",BLCK_ACT_INVITE,200));
    assert(!blck_authorized(&phoneC,"workshop",BLCK_ACT_REMOVE,200));
    assert(!blck_authorized(&phoneB,"other-room",BLCK_ACT_READ,200));
    assert(!blck_authorized(&guest,"workshop",BLCK_ACT_READ,200));
    assert(!blck_authorized(&phoneB,"workshop",BLCK_ACT_POST,300));
    phoneB.revoked=1;
    assert(!blck_authorized(&phoneB,"workshop",BLCK_ACT_READ,200));
    phoneC.verified=0;
    assert(!blck_authorized(&phoneC,"workshop",BLCK_ACT_POST,200));
    assert(!blck_authorized(NULL,"workshop",BLCK_ACT_POST,200));
    assert(!blck_authorized(&phoneA,"workshop",(blck_action)99,200));
    assert(!blck_authorized(&phoneA,"",BLCK_ACT_READ,200));
    puts("PASS authz: PhoneA/PhoneB/PhoneC");
    return 0;
}
