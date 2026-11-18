#include "blcksms_frame.h"
#include <assert.h>
#include <stdio.h>
#include <string.h>
static const char msgid[]="1234567890abcdef1234567890abcdef";
int main(void) {
    static char frames[BLCKSMS_PARTS_MAX][BLCKSMS_FRAME_TEXT];
    static uint8_t sealed[BLCKSMS_SEALED_MAX];
    static uint8_t restored[BLCKSMS_SEALED_MAX];
    blcksms_frame_collector collector;
    size_t parts,n,i,count;
    unsigned k;
    for (i=0u;i<sizeof sealed;i++)
        sealed[i]=(uint8_t)((i*37u + (i>>3u))&255u);
    for(k=1u;k<=3u;k++) {
        size_t len=(k==1u?1u:(k==2u?191u:BLCKSMS_SEALED_MAX));
        assert(blcksms_frame_pack(sealed,len,msgid,frames,BLCKSMS_PARTS_MAX,&parts)==0);
        assert(parts>0u && parts<=BLCKSMS_PARTS_MAX);
        for(i=0u;i<parts;i++) assert(strlen(frames[i])<=BLCKSMS_FRAME_LIMIT);
        blcksms_frame_reset(&collector);
        for(i=parts;i>0u;i--) {
            blcksms_frame_result r;
            size_t index=i-1u;
            r=blcksms_frame_receive(&collector,frames[index],restored,
                                    sizeof restored,&n);
            if (i>1u) assert(r==BLCKSMS_FRAME_PENDING);
            else assert(r==BLCKSMS_FRAME_COMPLETE);
        }
        assert(n==len && memcmp(restored,sealed,len)==0);
        assert(blcksms_frame_receive(&collector,frames[0],restored,
                 sizeof restored,&n)==BLCKSMS_FRAME_ERROR); /* replay */
    }
    assert(blcksms_frame_pack(sealed,190u,msgid,frames,BLCKSMS_PARTS_MAX,&count)==0);
    assert(count>1u);
    blcksms_frame_reset(&collector);
    assert(blcksms_frame_receive(&collector,frames[0],restored,
        sizeof restored,&n)==BLCKSMS_FRAME_PENDING);
    assert(blcksms_frame_receive(&collector,frames[0],restored,
        sizeof restored,&n)==BLCKSMS_FRAME_PENDING); /* exact duplicate */
    { /* conflicting duplicate must be rejected */
        char damaged[BLCKSMS_FRAME_TEXT];
        memcpy(damaged,frames[0],sizeof damaged);
        damaged[46]=(damaged[46]=='A'?'B':'A');
        assert(blcksms_frame_receive(&collector,damaged,restored,
             sizeof restored,&n)==BLCKSMS_FRAME_ERROR);
    }
    { /* a new message cannot be spliced into current assembly */
        char wrong[BLCKSMS_FRAME_TEXT];
        memcpy(wrong,frames[1],sizeof wrong);
        wrong[7]='a';
        assert(blcksms_frame_receive(&collector,wrong,restored,
             sizeof restored,&n)==BLCKSMS_FRAME_ERROR);
    }
    { /* invalid char and undersized output */
        char damaged[BLCKSMS_FRAME_TEXT];
        memcpy(damaged,frames[1],sizeof damaged);
        damaged[46]='.';
        assert(blcksms_frame_receive(&collector,damaged,restored,
             sizeof restored,&n)==BLCKSMS_FRAME_ERROR);
    }
    assert(blcksms_frame_pack(sealed,0,msgid,frames,BLCKSMS_PARTS_MAX,&count)<0);
    assert(blcksms_frame_pack(sealed,190,msgid,frames,1,&count)<0);
    assert(blcksms_frame_pack(sealed,190,"hello",frames,BLCKSMS_PARTS_MAX,&count)<0);
    assert(blcksms_frame_pack(sealed,190,msgid,frames,BLCKSMS_PARTS_MAX,&count)==0);
    blcksms_frame_reset(&collector);
    for(i=0;i<count;i++) {
        blcksms_frame_result r=blcksms_frame_receive(&collector,frames[i],
              restored,1u,&n);
        if(i+1u<count) assert(r==BLCKSMS_FRAME_PENDING);
        else assert(r==BLCKSMS_FRAME_ERROR); /* no overflow */
    }
    puts("PASS SMS frame: bounds, full payload, reorder, duplicate, corruption");
    return 0;
}
