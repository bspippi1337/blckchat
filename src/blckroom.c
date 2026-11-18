#include "blckroom.h"
#include "blcksecure.h"
#include "blcksms.h"
#include <stdint.h>
#include <string.h>

static int valid_name(const char *name,size_t max) {
    size_t i;
    if (!name || !*name) return 0;
    for (i=0;i<=max && name[i];i++) {
        unsigned char c=(unsigned char)name[i];
        if (c<33u || c>126u || c=='|' || c=='\\' || c=='/') return 0;
    }
    return i>0u && i<=max;
}
static int valid_text(const char *s) {
    size_t i=0u,n;
    if (!s) return 0;
    n=strlen(s);
    if (!n || n>BLCK_MAX_TEXT) return 0;
    while (i<n) {
        unsigned char c=(unsigned char)s[i++];
        unsigned code,min,remaining,k;
        if (c<0x80u) {
            if ((c<32u && c!=10u && c!=9u) || c==127u) return 0;
            continue;
        }
        if (c>=0xC2u && c<=0xDFu) {code=c&31u;min=0x80u;remaining=1u;}
        else if (c>=0xE0u && c<=0xEFu) {code=c&15u;min=0x800u;remaining=2u;}
        else if (c>=0xF0u && c<=0xF4u) {code=c&7u;min=0x10000u;remaining=3u;}
        else return 0;
        if (remaining>n-i) return 0;
        for (k=0u;k<remaining;k++) {
            unsigned char d=(unsigned char)s[i++];
            if ((d&0xC0u)!=0x80u) return 0;
            code=(code<<6u)|(d&63u);
        }
        if (code<min || code>0x10FFFFu ||
            (code>=0xD800u && code<=0xDFFFu)) return 0;
    }
    return 1;
}
static void hex_encode(const uint8_t *raw,size_t n,char *hex) {
    static const char tab[]="0123456789abcdef";
    size_t i;
    for (i=0u;i<n;i++) {
        hex[2u*i]=tab[raw[i]>>4u];
        hex[2u*i+1u]=tab[raw[i]&15u];
    }
    hex[2u*n]='\0';
}
static int hex_decode(const char *s,uint8_t *out,size_t bytes) {
    size_t i;
    if (!s) return -1;
    for (i=0u;i<bytes*2u;i++) {
        unsigned char c=(unsigned char)s[i];
        int v;
        if (!c) return -1;
        if (c>='0' && c<='9') v=(int)c-'0';
        else if (c>='a' && c<='f') v=(int)c-'a'+10;
        else return -1;
        if (i&1u) out[i/2u]|=(uint8_t)v;
        else out[i/2u]=(uint8_t)(v<<4u);
    }
    return s[bytes*2u]=='\0'?0:-1;
}
static int digest_token(const blck_room *room,const uint8_t token[32],
                        uint8_t digest[32]) {
    uint8_t data[33];
    data[0]=(uint8_t)'S';
    memcpy(data+1u,token,32u);
    return blck_secure_mac(room->secret,data,sizeof data,digest);
}
static int token_new(blck_room *room,blck_session *s,
                     char text[BLCK_TOKEN_HEX]) {
    uint8_t token[32];
    int ok=blck_secure_random(token,sizeof token);
    if (!ok) ok=digest_token(room,token,s->digest);
    if (!ok) hex_encode(token,sizeof token,text);
    blck_secure_zero(token,sizeof token);
    return ok;
}
static int digest_otp(const blck_room *room,const blck_invite *invite,
                      const char *otp,uint8_t digest[32]) {
    uint8_t data[1u+16u+8u];
    data[0]=(uint8_t)'O';
    if (!otp || strlen(otp)!=8u ||
        hex_decode(invite->locator,data+1u,16u)) return -1;
    memcpy(data+17u,otp,8u);
    return blck_secure_mac(room->secret,data,sizeof data,digest);
}
static blck_session *lookup_session(blck_room *room,const char *token,
                                    uint64_t now) {
    uint8_t raw[32],digest[32];
    blck_session *match=NULL;
    size_t i;
    if (!room || !room->initialized ||
        hex_decode(token,raw,sizeof raw)) return NULL;
    if (digest_token(room,raw,digest)) {
        blck_secure_zero(raw,sizeof raw);
        return NULL;
    }
    blck_secure_zero(raw,sizeof raw);
    for (i=0u;i<BLCK_MAX_SESSIONS;i++) {
        blck_session *s=&room->sessions[i];
        if (s->active && now<s->expires &&
            blck_secure_equal(digest,s->digest,32u)) match=s;
    }
    blck_secure_zero(digest,sizeof digest);
    if (match && !match->admin) {
        size_t index=match->invite_index;
        if (index>=BLCK_MAX_INVITES || !room->invites[index].live ||
            room->invites[index].revoked || !room->invites[index].redeemed)
            return NULL;
    }
    return match;
}
static blck_invite *find_invite(blck_room *room,const char *locator,
                               size_t *index) {
    size_t i;
    if (!room || !room->initialized || !locator ||
        strlen(locator)!=32u) return NULL;
    for (i=0u;i<BLCK_MAX_INVITES;i++)
        if (room->invites[i].live &&
            strcmp(room->invites[i].locator,locator)==0) {
            if (index) *index=i;
            return &room->invites[i];
        }
    return NULL;
}
static blck_status allow_admin(blck_room *room,const char *token,
                               uint64_t now) {
    blck_session *s=lookup_session(room,token,now);
    return s && s->admin ? BLCK_OK : BLCK_UNAUTHORIZED;
}
static int ttl(uint64_t now,uint64_t seconds,uint64_t *until) {
    if (now>UINT64_MAX-seconds) return -1;
    *until=now+seconds;
    return 0;
}
blck_status blck_room_init(blck_room *room,const char *room_name,
                           const char *admin_name,uint64_t now,
                           char admin_token[BLCK_TOKEN_HEX]) {
    blck_session *admin;
    uint64_t expiry;
    if (!room || !admin_token || !valid_name(room_name,48u) ||
        !valid_name(admin_name,32u) ||
        ttl(now,BLCK_SESSION_TTL,&expiry)) return BLCK_BAD_INPUT;
    memset(room,0,sizeof *room);
    admin_token[0]='\0';
    if (blck_secure_random(room->secret,sizeof room->secret))
        return BLCK_INTERNAL;
    memcpy(room->room,room_name,strlen(room_name)+1u);
    admin=&room->sessions[0];
    if (token_new(room,admin,admin_token)) {
        blck_room_clear(room);return BLCK_INTERNAL;
    }
    memcpy(admin->display_name,admin_name,strlen(admin_name)+1u);
    admin->active=1;admin->admin=1;
    admin->invite_index=SIZE_MAX;admin->expires=expiry;
    room->next_sequence=1u;room->initialized=1;
    return BLCK_OK;
}
blck_status blck_room_invite(blck_room *room,const char *admin_token,
                             const char *phone,uint64_t now,
                             char locator[BLCK_INVITE_HEX]) {
    size_t i;
    uint8_t raw[16];
    uint64_t expiry;
    blck_invite *inv=NULL;
    if (!room || !locator || !blcksms_valid_e164(phone) ||
        ttl(now,BLCK_INVITE_TTL,&expiry)) return BLCK_BAD_INPUT;
    locator[0]='\0';
    if (allow_admin(room,admin_token,now)!=BLCK_OK)
        return BLCK_UNAUTHORIZED;
    for (i=0u;i<BLCK_MAX_INVITES;i++)
        if (!room->invites[i].live) {inv=&room->invites[i];break;}
    if (!inv) return BLCK_FULL;
    if (blck_secure_random(raw,sizeof raw)) return BLCK_INTERNAL;
    memset(inv,0,sizeof *inv);
    hex_encode(raw,sizeof raw,inv->locator);
    blck_secure_zero(raw,sizeof raw);
    memcpy(inv->phone,phone,strlen(phone)+1u);
    inv->live=1;inv->invite_expires=expiry;
    memcpy(locator,inv->locator,BLCK_INVITE_HEX);
    return BLCK_OK;
}
static int generate_otp(char otp[BLCK_OTP_TEXT]) {
    size_t i=0u;
    while (i<8u) {
        uint8_t b;
        if (blck_secure_random(&b,1u)) return -1;
        if (b<250u) otp[i++]=(char)('0'+(b%10u));
    }
    otp[8]='\0';
    return 0;
}
blck_status blck_room_issue_otp(blck_room *room,const char *locator,
                                uint64_t now,char otp[BLCK_OTP_TEXT]) {
    uint8_t digest[32];
    blck_invite *invite;
    uint64_t expiry;
    if (!room || !otp || ttl(now,BLCK_OTP_TTL,&expiry))
        return BLCK_BAD_INPUT;
    otp[0]='\0';
    invite=find_invite(room,locator,NULL);
    if (!invite || invite->revoked || invite->redeemed)
        return BLCK_UNAUTHORIZED;
    if (now>=invite->invite_expires) return BLCK_EXPIRED;
    if (invite->issues>=BLCK_OTP_MAX_ISSUES ||
        (invite->issues && now-invite->last_issued<BLCK_OTP_COOLDOWN))
        return BLCK_LIMIT;
    if (generate_otp(otp)) return BLCK_INTERNAL;
    if (digest_otp(room,invite,otp,digest)) {
        blck_secure_zero(otp,BLCK_OTP_TEXT);return BLCK_INTERNAL;
    }
    memcpy(invite->otp_digest,digest,sizeof digest);
    blck_secure_zero(digest,sizeof digest);
    invite->otp_active=1;invite->otp_expires=expiry;
    invite->last_issued=now;invite->issues++;invite->failed=0;
    return BLCK_OK;
}
blck_status blck_room_join(blck_room *room,const char *locator,
                           const char *otp,const char *name,
                           uint64_t now,char guest_token[BLCK_TOKEN_HEX]) {
    blck_invite *invite;
    blck_session *s=NULL;
    uint8_t digest[32];
    uint64_t expiry;
    size_t index,i;
    if (!room || !guest_token || !valid_name(name,32u) ||
        ttl(now,BLCK_SESSION_TTL,&expiry)) return BLCK_BAD_INPUT;
    guest_token[0]='\0';
    invite=find_invite(room,locator,&index);
    if (!invite || invite->revoked || invite->redeemed || !invite->otp_active)
        return BLCK_UNAUTHORIZED;
    if (now>=invite->invite_expires || now>=invite->otp_expires)
        return BLCK_EXPIRED;
    if (invite->failed>=BLCK_OTP_MAX_GUESSES) return BLCK_LIMIT;
    if (digest_otp(room,invite,otp,digest)) return BLCK_UNAUTHORIZED;
    if (!blck_secure_equal(digest,invite->otp_digest,sizeof digest)) {
        blck_secure_zero(digest,sizeof digest);
        invite->failed++;
        if (invite->failed>=BLCK_OTP_MAX_GUESSES) invite->otp_active=0;
        return BLCK_UNAUTHORIZED;
    }
    blck_secure_zero(digest,sizeof digest);
    for (i=1u;i<BLCK_MAX_SESSIONS;i++)
        if (!room->sessions[i].active || now>=room->sessions[i].expires) {
            s=&room->sessions[i];break;
        }
    if (!s) return BLCK_FULL;
    memset(s,0,sizeof *s);
    if (token_new(room,s,guest_token)) return BLCK_INTERNAL;
    memcpy(s->display_name,name,strlen(name)+1u);
    s->active=1;s->expires=expiry;s->invite_index=index;
    s->visible_after=room->next_sequence-1u;
    invite->otp_active=0;invite->redeemed=1;
    blck_secure_zero(invite->otp_digest,sizeof invite->otp_digest);
    return BLCK_OK;
}
blck_status blck_room_post(blck_room *room,const char *session_token,
                           const char *text,uint64_t now,uint64_t *sequence) {
    blck_session *s;
    blck_message *m;
    if (!room || !sequence || !valid_text(text)) return BLCK_BAD_INPUT;
    s=lookup_session(room,session_token,now);
    if (!s) return BLCK_UNAUTHORIZED;
    if (room->message_count>=BLCK_MAX_MESSAGES ||
        room->next_sequence==UINT64_MAX) return BLCK_FULL;
    m=&room->messages[room->message_count];
    m->sequence=room->next_sequence++;
    m->timestamp=now;
    memcpy(m->author,s->display_name,strlen(s->display_name)+1u);
    memcpy(m->text,text,strlen(text)+1u);
    room->message_count++;
    *sequence=m->sequence;
    return BLCK_OK;
}
blck_status blck_room_read(blck_room *room,const char *session_token,
                           uint64_t since,uint64_t now,blck_message *out,
                           size_t capacity,size_t *count) {
    blck_session *s;
    size_t i,n=0u;
    if (!room || !count || (capacity && !out)) return BLCK_BAD_INPUT;
    *count=0u;
    s=lookup_session(room,session_token,now);
    if (!s) return BLCK_UNAUTHORIZED;
    for (i=0u;i<room->message_count;i++) {
        const blck_message *m=&room->messages[i];
        if (m->sequence<=since ||
            (!s->admin && m->sequence<=s->visible_after)) continue;
        if (n>=capacity) break;
        out[n++]=*m;
    }
    *count=n;return BLCK_OK;
}
blck_status blck_room_revoke(blck_room *room,const char *admin_token,
                             const char *locator,uint64_t now) {
    blck_invite *inv;
    size_t index,i;
    if (!room) return BLCK_BAD_INPUT;
    if (allow_admin(room,admin_token,now)!=BLCK_OK)
        return BLCK_UNAUTHORIZED;
    inv=find_invite(room,locator,&index);
    if (!inv) return BLCK_BAD_INPUT;
    inv->revoked=1;inv->otp_active=0;
    blck_secure_zero(inv->otp_digest,sizeof inv->otp_digest);
    for (i=1u;i<BLCK_MAX_SESSIONS;i++)
        if (room->sessions[i].active &&
            room->sessions[i].invite_index==index) {
            room->sessions[i].active=0;
            blck_secure_zero(room->sessions[i].digest,
                             sizeof room->sessions[i].digest);
        }
    return BLCK_OK;
}
void blck_room_clear(blck_room *room) {
    if (room) blck_secure_zero(room,sizeof *room);
}
