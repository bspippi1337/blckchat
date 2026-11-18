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
    blck_authz_session pippi=session("PhoneA",BLCK_ROLE_ADMIN);
    blck_authz_session trond=session("PhoneB",BLCK_ROLE_MEMBER);
    blck_authz_session thomas=session("PhoneC",BLCK_ROLE_MEMBER);
    blck_authz_session guest=session("NewGuest",BLCK_ROLE_INVITED);
    assert(blck_authorized(&pippi,"workshop",BLCK_ACT_INVITE,200));
    assert(blck_authorized(&pippi,"workshop",BLCK_ACT_POST,200));
    assert(blck_authorized(&trond,"workshop",BLCK_ACT_POST,200));
    assert(blck_authorized(&thomas,"workshop",BLCK_ACT_READ,200));
    assert(!blck_authorized(&trond,"workshop",BLCK_ACT_INVITE,200));
    assert(!blck_authorized(&thomas,"workshop",BLCK_ACT_REMOVE,200));
    assert(!blck_authorized(&trond,"other-room",BLCK_ACT_READ,200));
    assert(!blck_authorized(&guest,"workshop",BLCK_ACT_READ,200));
    assert(!blck_authorized(&trond,"workshop",BLCK_ACT_POST,300));
    trond.revoked=1;
    assert(!blck_authorized(&trond,"workshop",BLCK_ACT_READ,200));
    thomas.verified=0;
    assert(!blck_authorized(&thomas,"workshop",BLCK_ACT_POST,200));
    assert(!blck_authorized(NULL,"workshop",BLCK_ACT_POST,200));
    assert(!blck_authorized(&pippi,"workshop",(blck_action)99,200));
    assert(!blck_authorized(&pippi,"",BLCK_ACT_READ,200));
    puts("PASS authz: PhoneA/PhoneB/PhoneC");
    return 0;
}
