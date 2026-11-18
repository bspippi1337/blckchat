#include "blckroom.h"
#include "blcksecure.h"
#include <assert.h>
#include <stdio.h>
#include <string.h>
static void must(blck_status got,blck_status expected,const char *where) {
    if(got!=expected) {
        fprintf(stderr,"FAIL %s: %d not %d\n",where,(int)got,(int)expected);
        assert(got==expected);
    }
}
int main(void) {
    static blck_room room;
    char pippi[BLCK_TOKEN_HEX],trond[BLCK_TOKEN_HEX],thomas[BLCK_TOKEN_HEX];
    char invite_b[BLCK_INVITE_HEX],invite_c[BLCK_INVITE_HEX],otp[BLCK_OTP_TEXT];
    blck_message inbox[8];
    size_t n=0;
    uint64_t seq=0;
    must(blck_room_init(&room,"workshop","Pippi",1000,pippi),BLCK_OK,"init");
    must(blck_room_invite(&room,pippi,"+4799999999",1001,invite_b),BLCK_OK,"invite Trond");
    must(blck_room_invite(&room,pippi,"+4799999998",1002,invite_c),BLCK_OK,"invite Thomas");
    assert(strcmp(invite_b,invite_c)!=0);
    must(blck_room_issue_otp(&room,invite_b,1003,otp),BLCK_OK,"OTP B");
    must(blck_room_join(&room,invite_b,"11111111","Trond",1004,trond),BLCK_UNAUTHORIZED,"invalid OTP");
    must(blck_room_join(&room,invite_b,otp,"Trond",1005,trond),BLCK_OK,"Trond joins");
    must(blck_room_join(&room,invite_b,otp,"Trond",1006,thomas),BLCK_UNAUTHORIZED,"replay rejected");
    must(blck_room_post(&room,pippi,"Pippi online",1010,&seq),BLCK_OK,"Pippi sends");
    assert(seq==1u);
    must(blck_room_read(&room,trond,0,1011,inbox,8,&n),BLCK_OK,"Trond reads");
    assert(n==1u && strcmp(inbox[0].author,"Pippi")==0);
    must(blck_room_issue_otp(&room,invite_c,1012,otp),BLCK_OK,"OTP C");
    must(blck_room_join(&room,invite_c,otp,"Thomas",1013,thomas),BLCK_OK,"Thomas joins");
    must(blck_room_read(&room,thomas,0,1014,inbox,8,&n),BLCK_OK,"Thomas history");
    assert(n==0u);
    must(blck_room_post(&room,trond,"Hei Pippi og Thomas!",1015,&seq),BLCK_OK,"Trond sends");
    must(blck_room_post(&room,thomas,"God dag!",1016,&seq),BLCK_OK,"Thomas sends");
    must(blck_room_read(&room,pippi,0,1017,inbox,8,&n),BLCK_OK,"Pippi reads");
    assert(n==3u && strcmp(inbox[1].author,"Trond")==0 &&
           strcmp(inbox[2].author,"Thomas")==0);
    must(blck_room_read(&room,thomas,0,1017,inbox,8,&n),BLCK_OK,"Thomas reads");
    assert(n==2u && strcmp(inbox[0].author,"Trond")==0);
    must(blck_room_post(&room,trond,"Hei, verden!",1018,&seq),BLCK_OK,"UTF-8");
    must(blck_room_post(&room,trond,"\xc0\xaf",1019,&seq),BLCK_BAD_INPUT,"invalid UTF-8");
    must(blck_room_revoke(&room,trond,invite_c,1020),BLCK_UNAUTHORIZED,"Trond cannot revoke");
    must(blck_room_revoke(&room,pippi,invite_c,1020),BLCK_OK,"Pippi revokes");
    must(blck_room_post(&room,thomas,"denied",1021,&seq),BLCK_UNAUTHORIZED,"Thomas blocked");
    must(blck_room_read(&room,thomas,0,1021,inbox,8,&n),BLCK_UNAUTHORIZED,"Thomas cannot read");
    must(blck_room_read(&room,trond,0,87406,inbox,8,&n),BLCK_UNAUTHORIZED,"session expired");
    blck_secure_zero(otp,sizeof otp);
    blck_secure_zero(trond,sizeof trond);
    blck_secure_zero(thomas,sizeof thomas);
    blck_secure_zero(pippi,sizeof pippi);
    blck_room_clear(&room);
    puts("PASS room: Pippi, Trond, Thomas / one-time OTP / isolation / revocation");
    return 0;
}
