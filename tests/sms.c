#include "blcksms.h"
#include <assert.h>
#include <string.h>
#include <stdio.h>

int main(void) {
    blcksms_outgoing sms;
    assert(blcksms_valid_e164("+4799999999"));
    assert(blcksms_valid_e164("+15551234567"));
    assert(!blcksms_valid_e164("99999999"));
    assert(!blcksms_valid_e164("+0"));
    assert(!blcksms_valid_e164("+47 12345678"));
    assert(blcksms_invitation(&sms,"+4799999999",
        "Invite_abcd12345XYZ")==BLCKSMS_OK);
    assert(strcmp(sms.recipient,"+4799999999")==0);
    assert(strstr(sms.body,"join.html#i=Invite_abcd12345XYZ"));
    assert(!strstr(sms.body,"token="));
    assert(blcksms_invitation(&sms,"+4799999999",
        "../escape")==BLCKSMS_INVALID);
    assert(blcksms_invitation(&sms,"+4799999999",
        "Injected-\r\nSEND")==BLCKSMS_INVALID);
    assert(blcksms_login_code(&sms,"+4799999999",
        "12345678")==BLCKSMS_OK);
    assert(strstr(sms.body,"12345678"));
    assert(blcksms_login_code(&sms,"+4799999999",
        "1234567X")==BLCKSMS_INVALID);
    assert(blcksms_login_code(&sms,"+4799999999",
        "1234567")==BLCKSMS_INVALID);
    puts("BLCKSMS tests PASS");
    return 0;
}
