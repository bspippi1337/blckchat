#include "blckui.h"
#include <string.h>
#include <stdio.h>
#include <math.h>

static bsui_color rgb(unsigned r, unsigned g, unsigned b) {
    bsui_color c={(uint8_t)r,(uint8_t)g,(uint8_t)b,255u}; return c;
}
bsui_theme bsui_theme_dark(void) {
    bsui_theme t;
    t.background=rgb(10,14,20); t.panel=rgb(16,22,31);
    t.elevated=rgb(26,34,46); t.accent=rgb(99,235,206);
    t.foreground=rgb(236,241,245); t.secondary=rgb(153,167,183);
    t.border=rgb(43,56,70); t.sender=rgb(30,89,84);
    t.receiver=rgb(31,42,56); t.composer=rgb(24,32,43);
    t.spacing=8.0f; t.radius=16.0f; t.sidebar=240.0f;
    return t;
}
bsui_theme bsui_theme_light(void) {
    bsui_theme t;
    t.background=rgb(245,247,250); t.panel=rgb(255,255,255);
    t.elevated=rgb(233,239,243); t.accent=rgb(0,119,110);
    t.foreground=rgb(19,32,42); t.secondary=rgb(92,108,119);
    t.border=rgb(212,221,227); t.sender=rgb(199,236,224);
    t.receiver=rgb(234,239,244); t.composer=rgb(255,255,255);
    t.spacing=8.0f; t.radius=16.0f; t.sidebar=240.0f;
    return t;
}
void bsui_scene_init(bsui_scene *s, float w, float h, bsui_theme t) {
    if (!s) return;
    memset(s,0,sizeof(*s)); s->width=w; s->height=h; s->theme=t;
}
int bsui_box(bsui_scene *s, bsui_rect r, bsui_color color, float radius) {
    bsui_command *c;
    if (!s||s->count>=BSUI_MAX_COMMANDS||r.w<=0.0f||r.h<=0.0f) return -1;
    c=&s->commands[s->count++]; memset(c,0,sizeof(*c));
    c->kind=radius>0.0f?BSUI_ROUNDED_RECT:BSUI_RECT;
    c->rect=r;c->color=color;c->radius=radius;
    return 0;
}
int bsui_label(bsui_scene *s,float x,float y,const char *text,
               bsui_color color,bsui_text_style style) {
    bsui_command *c; size_t n;
    if (!s||!text||s->count>=BSUI_MAX_COMMANDS) return -1;
    n=strlen(text);
    if (n>=BSUI_MAX_TEXT) return -1;
    c=&s->commands[s->count++];memset(c,0,sizeof(*c));
    c->kind=BSUI_TEXT;c->rect.x=x;c->rect.y=y;c->color=color;
    c->text_style=style;memcpy(c->text,text,n+1u);
    return 0;
}
static float minf(float a,float b){return a<b?a:b;}
int bsui_chat_layout(bsui_scene *s,const char *room,
                     const bsui_message *messages,size_t count) {
    float w,h,side,top=74.0f,body,padding=24.0f,bubble;
    size_t i,start;
    bsui_theme t;
    if (!s||!room||s->width<280.0f||s->height<320.0f||
        (count&&!messages)) return -1;
    s->count=0;w=s->width;h=s->height;t=s->theme;
    side=w>=850.0f?t.sidebar:0.0f;body=w-side;
    bsui_box(s,(bsui_rect){0,0,w,h},t.background,0);
    if (side>0.0f) {
        bsui_box(s,(bsui_rect){0,0,side,h},t.panel,0);
        bsui_box(s,(bsui_rect){side-1.0f,0,1.0f,h},t.border,0);
        bsui_label(s,24,46,"BLCK / CHAT",t.accent,BSUI_TEXT_TITLE);
        bsui_label(s,24,100,"CONVERSATIONS",t.secondary,BSUI_TEXT_CAPTION);
        bsui_box(s,(bsui_rect){14,124,side-28,56},t.elevated,12);
        bsui_label(s,30,159,room,t.foreground,BSUI_TEXT_BODY);
    }
    bsui_box(s,(bsui_rect){side,0,body,top},t.panel,0);
    bsui_box(s,(bsui_rect){side,top-1.0f,body,1.0f},t.border,0);
    bsui_label(s,side+padding,44,room,t.foreground,BSUI_TEXT_TITLE);
    bsui_label(s,w-112,44,"PRIVATE",t.accent,BSUI_TEXT_CAPTION);
    bubble=minf(body-2*padding,440.0f);
    start=count>5u?count-5u:0u;
    for (i=start;i<count;i++) {
        float y=top+30.0f+(float)(i-start)*81.0f;
        float x=messages[i].mine?w-padding-bubble:side+padding;
        if (y+67.0f>h-108.0f) break;
        bsui_box(s,(bsui_rect){x,y,bubble,66.0f},
                 messages[i].mine?t.sender:t.receiver,14);
        bsui_label(s,x+14,y+23,messages[i].author,
                   t.accent,BSUI_TEXT_CAPTION);
        bsui_label(s,x+14,y+49,messages[i].message,
                   t.foreground,BSUI_TEXT_BODY);
    }
    bsui_box(s,(bsui_rect){side,h-94.0f,body,94.0f},t.panel,0);
    bsui_box(s,(bsui_rect){side+padding,h-78.0f,
                 body-2.0f*padding,54.0f},t.composer,14);
    bsui_label(s,side+padding+18,h-45.0f,"Write a message...",
               t.secondary,BSUI_TEXT_BODY);
    bsui_box(s,(bsui_rect){w-padding-55,h-70,46,38},t.accent,12);
    bsui_label(s,w-padding-41,h-46,">",t.background,BSUI_TEXT_TITLE);
    return 0;
}
static void color(FILE *f,bsui_color c) {
    fprintf(f,"#%02x%02x%02x",(unsigned)c.r,(unsigned)c.g,(unsigned)c.b);
}
static void escaped(FILE *f,const char *s) {
    while (*s) {
        switch (*s) {
        case '&': fputs("&amp;",f);break;
        case '<': fputs("&lt;",f);break;
        case '>': fputs("&gt;",f);break;
        case '"': fputs("&quot;",f);break;
        case '\'': fputs("&apos;",f);break;
        default: fputc((unsigned char)*s,f);break;
        }
        ++s;
    }
}
int bsui_export_svg(const bsui_scene *s,const char *name) {
    FILE *f;size_t i;
    if(!s||!name||!(f=fopen(name,"wb")))return -1;
    fprintf(f,"<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"%.0f\" height=\"%.0f\" viewBox=\"0 0 %.0f %.0f\">\n",s->width,s->height,s->width,s->height);
    for(i=0;i<s->count;i++) {
        const bsui_command *c=&s->commands[i];
        if(c->kind==BSUI_TEXT) {
            int sz=c->text_style==BSUI_TEXT_TITLE?20:(c->text_style==BSUI_TEXT_CAPTION?11:15);
            fprintf(f,"<text x=\"%.1f\" y=\"%.1f\" font-family=\"system-ui,sans-serif\" font-size=\"%d\" fill=\"",c->rect.x,c->rect.y,sz);
            color(f,c->color);fputs("\">",f);escaped(f,c->text);fputs("</text>\n",f);
        } else {
            fprintf(f,"<rect x=\"%.1f\" y=\"%.1f\" width=\"%.1f\" height=\"%.1f\" rx=\"%.1f\" fill=\"",c->rect.x,c->rect.y,c->rect.w,c->rect.h,c->radius);
            color(f,c->color);fputs("\"/>\n",f);
        }
    }
    fputs("</svg>\n",f);
    return fclose(f)==0?0:-1;
}
