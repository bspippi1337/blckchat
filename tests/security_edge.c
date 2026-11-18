#include "blckroom.h"
#include <assert.h>
#include <stdio.h>
#include <string.h>
static blck_room room;
static void check(blck_status got,blck_status expected) {
    if(got!=expected) fprintf(stderr,"got %d expected %d\n",(int)got,(int)expected);
    assert(got==expected);
}
int main(void) {
    char admin[BLCK_TOKEN_HEX],member[BLCK_TOKEN_HEX];
    char locator[BLCK_INVITE_HEX],otp[BLCK_OTP_TEXT],bogus[BLCK_TOKEN_HEX];
    uint64_t seq=0;
    size_t count=42;
    blck_message message;
    memset(bogus,'0',sizeof bogus-1u);bogus[sizeof bogus-1u]='\0';
    check(blck_room_init(&room,"Room","PhoneA",1,admin),BLCK_OK);
    check(blck_room_invite(&room,bogus,"+4799999999",3,locator),BLCK_UNAUTHORIZED);
    check(blck_room_invite(&room,admin,"+47 a",3,locator),BLCK_BAD_INPUT);
    check(blck_room_invite(&room,admin,"+4799999999",5,locator),BLCK_OK);
    check(blck_room_issue_otp(&room,locator,6,otp),BLCK_OK);
    check(blck_room_issue_otp(&room,locator,7,otp),BLCK_LIMIT);
    check(blck_room_issue_otp(&room,locator,4,otp),BLCK_LIMIT); /* clock rollback */
    check(blck_room_join(&room,locator,"12345678","PhoneB",7,member),BLCK_UNAUTHORIZED);
    check(blck_room_join(&room,locator,"12345678","PhoneB",8,member),BLCK_UNAUTHORIZED);
    check(blck_room_join(&room,locator,"12345678","PhoneB",9,member),BLCK_UNAUTHORIZED);
    check(blck_room_join(&room,locator,"12345678","PhoneB",10,member),BLCK_UNAUTHORIZED);
    check(blck_room_join(&room,locator,"12345678","PhoneB",11,member),BLCK_UNAUTHORIZED);
    check(blck_room_join(&room,locator,"12345678","PhoneB",12,member),BLCK_UNAUTHORIZED);
    check(blck_room_issue_otp(&room,locator,66,otp),BLCK_OK);
    check(blck_room_join(&room,locator,otp,"PhoneB",666,member),BLCK_EXPIRED);
    check(blck_room_issue_otp(&room,locator,126,otp),BLCK_OK);
    check(blck_room_join(&room,locator,otp,"PhoneB",127,member),BLCK_OK);
    check(blck_room_issue_otp(&room,locator,187,otp),BLCK_UNAUTHORIZED);
    check(blck_room_post(&room,bogus,"unauthorized",128,&seq),BLCK_UNAUTHORIZED);
    check(blck_room_post(&room,member,"\x01",129,&seq),BLCK_BAD_INPUT);
    check(blck_room_post(&room,member,"\xed\xa0\x80",129,&seq),BLCK_BAD_INPUT);
    check(blck_room_post(&room,member,"\xf4\x90\x80\x80",129,&seq),BLCK_BAD_INPUT);
    check(blck_room_post(&room,member,"A\nB",129,&seq),BLCK_OK);
    check(blck_room_read(&room,member,0,130,&message,1,&count),BLCK_OK);
    assert(count==1u && strcmp(message.author,"PhoneB")==0 &&
           strcmp(message.text,"A\nB")==0);
    check(blck_room_read(&room,bogus,0,130,&message,1,&count),BLCK_UNAUTHORIZED);
    check(blck_room_revoke(&room,admin,locator,131),BLCK_OK);
    check(blck_room_post(&room,member,"must reject",132,&seq),BLCK_UNAUTHORIZED);
    blck_room_clear(&room);
    puts("PASS security edges: cooldown, lockout, replay, UTF-8, revoked sessions");
    return 0;
}
