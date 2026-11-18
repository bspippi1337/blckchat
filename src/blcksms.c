#include "blcksms.h"
#include <stdio.h>
#include <string.h>

static int safe_id(const char *id) {
    size_t n,i;
    if (!id) return 0;
    n=strlen(id);
    if (n<12u || n>BLCKSMS_INVITE_ID_MAX) return 0;
    for (i=0u;i<n;i++) {
        const char c=id[i];
        if (!((c>='A' && c<='Z') || (c>='a' && c<='z') ||
              (c>='0' && c<='9') || c=='-' || c=='_')) return 0;
    }
    return 1;
}

int blcksms_valid_e164(const char *s) {
    size_t i,n;
    if (!s || s[0]!='+') return 0;
    n=strlen(s);
    if (n<9u || n>16u || s[1]<'1' || s[1]>'9') return 0;
    for (i=2u;i<n;i++) {
        if (s[i]<'0' || s[i]>'9') return 0;
    }
    return 1;
}

static blcksms_result finish(blcksms_outgoing *out,const char *phone,int n) {
    if (n<0 || (size_t)n>=sizeof(out->body)) {
        out->body[0]='\0';
        out->recipient[0]='\0';
        return BLCKSMS_NO_SPACE;
    }
    (void)strcpy(out->recipient,phone);
    return BLCKSMS_OK;
}

blcksms_result blcksms_invitation(
    blcksms_outgoing *out, const char *phone, const char *invite_id) {
    int n;
    if (!out || !blcksms_valid_e164(phone) || !safe_id(invite_id))
        return BLCKSMS_INVALID;
    n=snprintf(out->body,sizeof(out->body),
        "BLCKCHAT: Du er invitert! 1) Installer appen via "
        "https://bspippi1337.github.io/blckchat/join.html#i=%s "
        "2) Åpne SMS-lenken igjen for å bli med. Ingen GitHub-konto.",
        invite_id);
    return finish(out,phone,n);
}

blcksms_result blcksms_login_code(
    blcksms_outgoing *out, const char *phone, const char *code) {
    size_t i;
    int n;
    if (!out || !blcksms_valid_e164(phone) ||
        !code || strlen(code)!=8u) return BLCKSMS_INVALID;
    for (i=0u;i<8u;i++) {
        if (code[i]<'0' || code[i]>'9') return BLCKSMS_INVALID;
    }
    n=snprintf(out->body,sizeof(out->body),
        "BLCKCHAT: Innloggingskoden din er %s. Gyldig i 10 minutter. "
        "Del aldri koden med andre.",code);
    return finish(out,phone,n);
}
