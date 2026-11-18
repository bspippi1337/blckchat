#include "blcksms_frame.h"
#include <stdio.h>
#include <string.h>

static const char b64[] =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";
static int id_ok(const char *id) {
    size_t i;
    if (!id) return 0;
    for (i=0u;i<32u;i++) {
        unsigned char c=(unsigned char)id[i];
        if (!((c>='0'&&c<='9')||(c>='a'&&c<='f'))) return 0;
    }
    return id[32]=='\0';
}
static int val(unsigned char c) {
    if (c>='A'&&c<='Z') return (int)(c-'A');
    if (c>='a'&&c<='z') return (int)(c-'a'+26);
    if (c>='0'&&c<='9') return (int)(c-'0'+52);
    if (c=='-') return 62;
    if (c=='_') return 63;
    return -1;
}
static size_t text_size(size_t bytes) {
    return (bytes/3u)*4u+(bytes%3u==0u?0u:(bytes%3u==1u?2u:3u));
}
static void encode(const uint8_t *src, size_t bytes, char *out) {
    size_t i=0u,j=0u;
    while(i+2u<bytes) {
        unsigned x=((unsigned)src[i]<<16u)|((unsigned)src[i+1u]<<8u)|
                   (unsigned)src[i+2u];
        out[j++]=b64[(x>>18u)&63u];out[j++]=b64[(x>>12u)&63u];
        out[j++]=b64[(x>>6u)&63u];out[j++]=b64[x&63u];i+=3u;
    }
    if (bytes-i==1u) {
        unsigned x=src[i];
        out[j++]=b64[x>>2u];out[j++]=b64[(x&3u)<<4u];
    } else if (bytes-i==2u) {
        unsigned x=((unsigned)src[i]<<8u)|src[i+1u];
        out[j++]=b64[(x>>10u)&63u];out[j++]=b64[(x>>4u)&63u];
        out[j++]=b64[(x&15u)<<2u];
    }
    out[j]='\0';
}
static int decode(const char *text,size_t count,
                  uint8_t *out,size_t cap,size_t *written) {
    size_t i,j=0u;
    unsigned x=0u,bits=0u;
    if (!count || count%4u==1u) return -1;
    for (i=0u;i<count;i++) {
        int v=val((unsigned char)text[i]);
        if (v<0) return -1;
        x=(x<<6u)|(unsigned)v;
        bits+=6u;
        if (bits>=8u) {
            bits-=8u;
            if (j>=cap) return -1;
            out[j++]=(uint8_t)((x>>bits)&255u);
        }
        x&=(1u<<bits)-1u;
    }
    if (x!=0u) return -1; /* reject noncanonical trailing pad bits */
    *written=j;return 0;
}
int blcksms_frame_pack(const uint8_t *sealed,size_t sealed_len,
                       const char id[BLCKSMS_ID_TEXT],
                       char out[][BLCKSMS_FRAME_TEXT],
                       size_t available,size_t *frame_count) {
    char encoded[BLCKSMS_PARTS_MAX*BLCKSMS_PART_BYTES+1u];
    size_t total,segments,i;
    if (!sealed || !out || !frame_count || !id_ok(id) ||
        sealed_len==0u || sealed_len>BLCKSMS_SEALED_MAX) return -1;
    total=text_size(sealed_len);
    segments=(total+BLCKSMS_PART_BYTES-1u)/BLCKSMS_PART_BYTES;
    if (!segments || segments>BLCKSMS_PARTS_MAX ||
        available<segments) return -1;
    encode(sealed,sealed_len,encoded);
    for (i=0u;i<segments;i++) {
        size_t begin=i*BLCKSMS_PART_BYTES;
        size_t len=total-begin;
        int n;
        if (len>BLCKSMS_PART_BYTES) len=BLCKSMS_PART_BYTES;
        n=snprintf(out[i],BLCKSMS_FRAME_TEXT,
                   "BC42|1|%s|%02u/%02u|%.*s",id,
                   (unsigned)(i+1u),(unsigned)segments,
                   (int)len,encoded+begin);
        if (n<0 || n>(int)BLCKSMS_FRAME_LIMIT) return -1;
    }
    *frame_count=segments;
    return 0;
}
void blcksms_frame_reset(blcksms_frame_collector *s) {
    if (s) memset(s,0,sizeof *s);
}
static int two_digits(const char *p) {
    if (p[0]<'0'||p[0]>'9'||p[1]<'0'||p[1]>'9') return -1;
    return (p[0]-'0')*10+(p[1]-'0');
}
blcksms_frame_result blcksms_frame_receive(
    blcksms_frame_collector *s,const char *frame,
    uint8_t *out,size_t available,size_t *sealed_len) {
    size_t n=0u,i,partlen,offset=0u,decoded=0u;
    unsigned part,count;
    char id[BLCKSMS_ID_TEXT];
    char encoded[BLCKSMS_PARTS_MAX*BLCKSMS_PART_BYTES+1u];
    if (!s||!frame||!out||!sealed_len) return BLCKSMS_FRAME_ERROR;
    *sealed_len=0u;
    while (n<=BLCKSMS_FRAME_LIMIT && frame[n]) n++;
    if (n>BLCKSMS_FRAME_LIMIT || n<47u ||
        strncmp(frame,"BC42|1|",7u)!=0 || frame[39]!='|' ||
        frame[42]!='/' || frame[45]!='|') return BLCKSMS_FRAME_ERROR;
    memcpy(id,frame+7,32u);id[32]='\0';
    if (!id_ok(id)) return BLCKSMS_FRAME_ERROR;
    part=(unsigned)two_digits(frame+40);
    count=(unsigned)two_digits(frame+43);
    if (part==0u||count==0u||part>count||count>BLCKSMS_PARTS_MAX)
        return BLCKSMS_FRAME_ERROR;
    partlen=n-46u;
    if (partlen==0u || partlen>BLCKSMS_PART_BYTES ||
        (part<count && partlen!=BLCKSMS_PART_BYTES))
        return BLCKSMS_FRAME_ERROR;
    for (i=46u;i<n;i++)
        if (val((unsigned char)frame[i])<0)
            return BLCKSMS_FRAME_ERROR;
    if (s->complete) return BLCKSMS_FRAME_ERROR;
    if (!s->count) {
        memcpy(s->id,id,BLCKSMS_ID_TEXT);
        s->count=count;
    } else if (s->count!=count || strcmp(s->id,id)!=0)
        return BLCKSMS_FRAME_ERROR;
    i=part-1u;
    if (s->received[i]) {
        if ((size_t)s->sizes[i]!=partlen ||
            memcmp(s->chunks[i],frame+46u,partlen)!=0)
            return BLCKSMS_FRAME_ERROR;
    } else {
        memcpy(s->chunks[i],frame+46u,partlen);
        s->chunks[i][partlen]='\0';
        s->sizes[i]=(unsigned char)partlen;
        s->received[i]=1u;
        s->stored++;
    }
    if (s->stored<s->count) return BLCKSMS_FRAME_PENDING;
    for(i=0u;i<s->count;i++) {
        size_t len=s->sizes[i];
        if (!s->received[i] || offset+len>sizeof(encoded)-1u)
            return BLCKSMS_FRAME_ERROR;
        memcpy(encoded+offset,s->chunks[i],len);
        offset+=len;
    }
    if(decode(encoded,offset,out,available,&decoded) ||
       decoded==0u || decoded>BLCKSMS_SEALED_MAX)
        return BLCKSMS_FRAME_ERROR;
    *sealed_len=decoded;
    s->complete=1u;
    return BLCKSMS_FRAME_COMPLETE;
}
