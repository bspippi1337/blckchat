#include "blckui.h"
#include <stdio.h>
int main(int argc,char **argv) {
    bsui_scene scene;
    const bsui_message messages[]={
        {"MONICA","Good evening!",0},
        {"YOU","Welcome to BLCKCHAT 42.",1},
        {"MONICA","This looks much cleaner.",0},
        {"YOU","One channel, no GitHub account needed.",1}
    };
    const char *out=argc>1?argv[1]:"blckchat-preview.svg";
    bsui_scene_init(&scene,1080,720,bsui_theme_dark());
    if(bsui_chat_layout(&scene,"The Workshop",messages,4u)!=0||
       bsui_export_svg(&scene,out)!=0) {
        fputs("Preview export failed\n",stderr);return 1;
    }
    printf("Created %s\n",out);return 0;
}
