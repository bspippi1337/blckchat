/* BLCKCHAT C11 local CLI prototype: NOT networked or encrypted. */
#include <stdio.h>
#include <string.h>
#include <stdlib.h>
#include <time.h>

#define MAX_MESSAGE 2048u
#define MAX_LINE 4352u
static int name_ok(const char *s, size_t limit) {
    size_t i, n;
    if (!s || (n=strlen(s)) == 0 || n>limit) return 0;
    for (i=0;i<n;i++) {
        char c=s[i];
        if (!((c>='a'&&c<='z')||(c>='A'&&c<='Z')||
              (c>='0'&&c<='9')||c=='-'||c=='_')) return 0;
    }
    return 1;
}
static int location(const char *room, char *path, size_t size) {
    int n;
    if (!name_ok(room,48)) return 0;
    n=snprintf(path,size,"%s.blckchat",room);
    return n>0&&(size_t)n<size;
}
static void encode(FILE *f, const unsigned char *p, size_t n) {
    const char *hex="0123456789abcdef";
    size_t i;
    for(i=0;i<n;i++) { fputc(hex[p[i]>>4],f); fputc(hex[p[i]&15],f); }
}
static int digit(int c) {
    if(c>='0'&&c<='9')return c-'0';
    if(c>='a'&&c<='f')return c-'a'+10;
    return -1;
}
static int decode(const char *s, unsigned char *out, size_t capacity) {
    size_t i,n=strlen(s);
    if(n%2||n/2>capacity)return -1;
    for(i=0;i<n;i+=2) {
        int a=digit((unsigned char)s[i]),b=digit((unsigned char)s[i+1]);
        if(a<0||b<0)return -1;
        out[i/2]=(unsigned char)(a*16+b);
    }
    return (int)(n/2);
}
static int header(FILE *f) {
    char b[32];
    return fgets(b,sizeof b,f)&&strcmp(b,"BLCKCHAT1\n")==0;
}
static int init(const char *room) {
    char path[64];
    FILE *f;
    if(!location(room,path,sizeof path))return 2;
    f=fopen(path,"rb");
    if(f){fclose(f);fputs("Room exists\n",stderr);return 3;}
    f=fopen(path,"wb");
    if(!f){perror(path);return 4;}
    if(fputs("BLCKCHAT1\n",f)==EOF){fclose(f);return 4;}
    if(fclose(f)!=0)return 4;
    puts("Local room initialized (not private/encrypted).");
    return 0;
}
static int send_message(const char *room,const char *user,const char *message) {
    char path[64];
    FILE *f;
    size_t n;
    int err=0;
    if(!location(room,path,sizeof path)||!name_ok(user,32)||
       !message||(n=strlen(message))==0||n>MAX_MESSAGE)return 2;
    f=fopen(path,"rb");
    if(!f){perror(path);return 4;}
    if(!header(f)){fclose(f);return 5;}
    fclose(f);
    f=fopen(path,"ab");
    if(!f){perror(path);return 4;}
    if(fprintf(f,"M|%lld|%s|",(long long)time(NULL),user)<0)err=1;
    if(!err)encode(f,(const unsigned char *)message,n);
    if(fputc('\n',f)==EOF||ferror(f))err=1;
    if(fclose(f)!=0)err=1;
    if(err)return 4;
    puts("Saved locally. Not delivered to GitHub.");
    return 0;
}
static int read_messages(const char *room) {
    char path[64],line[MAX_LINE];
    unsigned char data[MAX_MESSAGE];
    FILE *f;
    int result=0;
    if(!location(room,path,sizeof path))return 2;
    f=fopen(path,"rb");
    if(!f){perror(path);return 4;}
    if(!header(f)){fclose(f);return 5;}
    while(fgets(line,sizeof line,f)) {
        char *timestamp,*user,*body,*sep,*end;
        int len;
        if(!strchr(line,'\n')){result=5;break;}
        line[strcspn(line,"\n")]=0;
        if(strncmp(line,"M|",2)!=0){result=5;break;}
        timestamp=line+2;
        sep=strchr(timestamp,'|');
        if(!sep){result=5;break;}
        *sep=0;
        user=sep+1;
        sep=strchr(user,'|');
        if(!sep){result=5;break;}
        *sep=0;
        body=sep+1;
        (void)strtoll(timestamp,&end,10);
        if(end==timestamp||*end||!name_ok(user,32)){result=5;break;}
        len=decode(body,data,MAX_MESSAGE);
        if(len<=0){result=5;break;}
        printf("[%s] %s: ",timestamp,user);
        if(fwrite(data,1,(size_t)len,stdout)!=(size_t)len){result=4;break;}
        putchar('\n');
    }
    if(ferror(f))result=4;
    if(fclose(f)!=0)result=4;
    return result;
}
int main(int argc,char **argv) {
    if(argc==3&&strcmp(argv[1],"init")==0)return init(argv[2]);
    if(argc==5&&strcmp(argv[1],"send")==0)
        return send_message(argv[2],argv[3],argv[4]);
    if(argc==3&&strcmp(argv[1],"read")==0)return read_messages(argv[2]);
    fputs("BLCKCHAT local prototype\n"
          "  blckchat init ROOM\n"
          "  blckchat send ROOM USER TEXT\n"
          "  blckchat read ROOM\n",stderr);
    return 2;
}
