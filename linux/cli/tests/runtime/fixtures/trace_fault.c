#define _GNU_SOURCE
/* VM-only syscall/phase fault harness. No product test hooks or arbitrary command mode. */
#include <sys/ptrace.h>
#include <sys/user.h>
#include <sys/wait.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <unistd.h>
#include <fcntl.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <time.h>

#define CLI "/opt/xrayfa-runtime/xrayfa"
#define CORE "/opt/xrayfa-runtime/xray"
#define JOURNAL "/run/xrayfa/session.json"
#define MAX_TASKS 4096
struct task { pid_t pid; int present, inject; } tasks[MAX_TASKS];
static long long ms(void){struct timespec t;clock_gettime(CLOCK_MONOTONIC,&t);return (long long)t.tv_sec*1000+t.tv_nsec/1000000;}
static int load(const char *path,char *b,size_t size){int fd=open(path,O_RDONLY|O_CLOEXEC|O_NOFOLLOW);if(fd<0)return -1;int n=read(fd,b,size-1);close(fd);if(n<0)return -1;b[n]=0;return n;}
static struct task *task(pid_t pid){for(int i=0;i<MAX_TASKS;i++)if(tasks[i].present&&tasks[i].pid==pid)return &tasks[i];for(int i=0;i<MAX_TASKS;i++)if(!tasks[i].present){tasks[i]=(struct task){pid,1,0};return &tasks[i];}return NULL;}
static int any(void){for(int i=0;i<MAX_TASKS;i++)if(tasks[i].present)return 1;return 0;}
static int trusted(const char *path){struct stat s;return !lstat(path,&s)&&S_ISREG(s.st_mode)&&s.st_uid==0&&!(s.st_mode&0022);}
static int fdpath(pid_t pid,int fd,char *b,size_t size){char path[128];snprintf(path,sizeof(path),"/proc/%d/fd/%d",pid,fd);int n=readlink(path,b,size-1);if(n<0)return -1;b[n]=0;return n;}
static int argument(pid_t pid,unsigned long array,int index,char *out,size_t size){
    errno=0;unsigned long address=ptrace(PTRACE_PEEKDATA,pid,(void *)(array+index*sizeof(unsigned long)),0);if(errno)return -1;
    size_t n=0;while(n<size-1){errno=0;unsigned long word=ptrace(PTRACE_PEEKDATA,pid,(void *)(address+n),0);if(errno)return -1;
        for(size_t i=0;i<sizeof(word)&&n<size-1;i++){char c=((char *)&word)[i];out[n++]=c;if(!c)return 0;}}
    out[size-1]=0;return 0;
}
static void dump_journal(const char *report){
    char path[4096],data[262144];snprintf(path,sizeof(path),"%s",report);char *slash=strrchr(path,'/');if(!slash)return;strcpy(slash+1,"inject-journal.json");
    int n=load(JOURNAL,data,sizeof(data));if(n<0)return;int fd=open(path,O_WRONLY|O_CREAT|O_EXCL|O_NOFOLLOW|O_CLOEXEC,0600);
    if(fd>=0){int total=0;while(total<n){ssize_t done=write(fd,data+total,n-total);if(done<0&&errno==EINTR)continue;if(done<=0)break;total+=(int)done;}fsync(fd);close(fd);}
}
static int phase_matches(const char *phase){
    char b[262144];if(load(JOURNAL,b,sizeof(b))<0)return 0;
    if(!strcmp(phase,"cleanup")||!strcmp(phase,"tool-gated")||!strcmp(phase,"nft-committed")){
        char *p=strstr(b,"\"pendingTool\"");if(!p||(p=strchr(p,'{'))==NULL)return 0;
        int depth=0,quoted=0,escape=0;char *start=p;
        for(;*p;p++){
            if(quoted){if(escape)escape=0;else if(*p=='\\')escape=1;else if(*p=='"')quoted=0;continue;}
            if(*p=='"'){quoted=1;continue;}if(*p=='{')depth++;if(*p=='}'&&--depth==0){*p=0;break;}
        }
        if(!strcmp(phase,"cleanup"))return strstr(start,"\"revert\"")&&strstr(start,"\"state\": \"reaped\"");
        return strstr(start,"\"path\": \"/usr/sbin/nft\"")&&strstr(start,!strcmp(phase,"tool-gated")?"\"state\": \"gated\"":"\"state\": \"reaped\"");
    }
    const char *p=strstr(b,"\"phase\"");if(!p)return 0;p=strchr(p,':');if(!p)return 0;
    p++;while(*p==' '||*p=='\n'||*p=='\t')p++;if(*p!='"')return 0;p++;
    return !strncmp(p,phase,strlen(phase))&&p[strlen(phase)]=='"';
}
static int detach_all(pid_t controller){
    for(int i=0;i<MAX_TASKS;i++)if(tasks[i].present&&tasks[i].pid!=controller)kill(tasks[i].pid,SIGSTOP);
    long long end=ms()+3000;pid_t resumed[MAX_TASKS];int count=0;
    while(any()&&ms()<end){
        int status;pid_t pid=waitpid(-1,&status,__WALL|WNOHANG);
        if(pid<=0){usleep(1000);continue;}
        struct task *t=task(pid);if(!t)return -1;
        if(WIFEXITED(status)||WIFSIGNALED(status)){t->present=0;continue;}
        if(WIFSTOPPED(status)){
            unsigned event=(unsigned)status>>16;
            if(event==PTRACE_EVENT_FORK||event==PTRACE_EVENT_VFORK||event==PTRACE_EVENT_CLONE){unsigned long child=0;ptrace(PTRACE_GETEVENTMSG,pid,0,&child);task((pid_t)child);kill((pid_t)child,SIGSTOP);}
            if(pid==controller){ptrace(PTRACE_CONT,pid,0,SIGKILL);continue;}
            if(ptrace(PTRACE_DETACH,pid,0,0)==0){t->present=0;resumed[count++]=pid;}
        }
    }
    for(int i=0;i<count;i++)kill(resumed[i],SIGCONT);
    return any()?-1:0;
}
int main(int argc,char **argv){
    if(argc!=6){fprintf(stderr,"usage: trace_fault exec-deny|kill-phase|dns-partial|dns-restore INSTANCE NODE PHASE REPORT\n");return 2;}
    int deny=!strcmp(argv[1],"exec-deny"),crash=!strcmp(argv[1],"kill-phase"),partial=!strcmp(argv[1],"dns-partial"),restore=!strcmp(argv[1],"dns-restore");
    if(!deny&&!crash&&!partial&&!restore)return 2;
    if(crash&&strcmp(argv[4],"leased")&&strcmp(argv[4],"core-gated")&&strcmp(argv[4],"tun-ready")&&strcmp(argv[4],"Running")&&strcmp(argv[4],"cleanup")&&strcmp(argv[4],"tool-gated")&&strcmp(argv[4],"nft-committed"))return 2;
    char marker[128],dmi[128];
    if(geteuid()!=0||load("/etc/xrayfa-vm-id",marker,sizeof(marker))<0||load("/sys/class/dmi/id/product_uuid",dmi,sizeof(dmi))<0)return 4;
    marker[strcspn(marker,"\r\n")]=0;dmi[strcspn(dmi,"\r\n")]=0;
    if(strcmp(marker,argv[2])||strcasecmp(dmi,argv[2])||!trusted(CLI)||!trusted(CORE))return 4;
    char *end;long node=strtol(argv[3],&end,10);if(*end||node<=0||node>2147483647)return 2;
    if(strncmp(argv[5],"/var/lib/xrayfa-fixture/scenarios/",34)||strstr(argv[5],".."))return 4;
    int report=open(argv[5],O_WRONLY|O_CREAT|O_EXCL|O_NOFOLLOW|O_CLOEXEC,0600);if(report<0)return 10;
    pid_t controller=fork();if(controller<0)return 7;
    if(controller==0){
        if(ptrace(PTRACE_TRACEME,0,0,0))_exit(126);
        raise(SIGSTOP);
        char *args[]={CLI,"run",argv[3],"--core",CORE,"--log-level","debug",NULL};
        char *env[]={"PATH=/usr/sbin:/usr/bin:/sbin:/bin","LANG=C","LC_ALL=C","SUDO_UID=1000",NULL};
        execve(CLI,args,env);_exit(127);
    }
    int status;waitpid(controller,&status,0);task(controller);
    long opts=PTRACE_O_TRACESYSGOOD|PTRACE_O_TRACEFORK|PTRACE_O_TRACEVFORK|PTRACE_O_TRACECLONE|PTRACE_O_TRACEEXEC|PTRACE_O_EXITKILL;
    ptrace(PTRACE_SETOPTIONS,controller,0,opts);ptrace(PTRACE_SYSCALL,controller,0,0);
    long long deadline=ms()+35000;int injected=0,killed=0,normal=-1,signum=0,timedout=0,asked_stop=0;
    while(any()&&ms()<deadline){
        pid_t pid=waitpid(-1,&status,__WALL|WNOHANG);if(pid<=0){usleep(200);continue;}
        struct task *t=task(pid);if(!t)break;
        if(WIFEXITED(status)||WIFSIGNALED(status)){
            t->present=0;if(pid==controller){if(WIFEXITED(status))normal=WEXITSTATUS(status);else signum=WTERMSIG(status);}continue;
        }
        if(!WIFSTOPPED(status))continue;
        unsigned event=(unsigned)status>>16;int sig=WSTOPSIG(status);
        if(event==PTRACE_EVENT_FORK||event==PTRACE_EVENT_VFORK||event==PTRACE_EVENT_CLONE){unsigned long child=0;ptrace(PTRACE_GETEVENTMSG,pid,0,&child);task((pid_t)child);}
        if(sig==(SIGTRAP|0x80)){
            struct user_regs_struct r;if(ptrace(PTRACE_GETREGS,pid,0,&r))break;
            if(t->inject){r.rax=(unsigned long)-EACCES;ptrace(PTRACE_SETREGS,pid,0,&r);t->inject=0;injected=1;}
            else if(!injected&&r.orig_rax==SYS_execveat&&(long)r.rax==-ENOSYS){
                char path[4096],arg[64]={0};int identified=fdpath(pid,(int)r.rdi,path,sizeof(path))>=0;
                if(identified&&(partial||restore))argument(pid,r.rdx,1,arg,sizeof(arg));
                if(identified&&((deny&&!strcmp(path,CORE))||((partial||restore)&&!strcmp(path,"/usr/bin/resolvectl")&&!strcmp(arg,partial?"domain":"revert")))){
                    fprintf(stderr,"INJECTED execveat pid=%d executable=%s errno=EACCES\n",pid,path);dump_journal(argv[5]);
                    r.orig_rax=(unsigned long)-1;ptrace(PTRACE_SETREGS,pid,0,&r);t->inject=1;
                }
            }
            if((crash||restore)&&pid==controller&&r.orig_rax==SYS_fsync&&(long)r.rax==0){
                char path[4096];if(fdpath(pid,(int)r.rdi,path,sizeof(path))>=0&&!strcmp(path,"/run/xrayfa")){
                    if((restore||!strcmp(argv[4],"cleanup"))&&!asked_stop&&phase_matches("Running")){kill(controller,SIGTERM);asked_stop=1;}
                    if(crash&&phase_matches(argv[4])){
                        killed=1;signum=SIGKILL;kill(controller,SIGKILL);ptrace(PTRACE_CONT,controller,0,SIGKILL);detach_all(controller);break;
                    }
                }
            }
        }
        int deliver=(sig==(SIGTRAP|0x80)||sig==SIGTRAP||sig==SIGSTOP)?0:sig;
        ptrace(PTRACE_SYSCALL,pid,0,deliver);
    }
    if(any()&&!killed){timedout=1;kill(controller,SIGKILL);detach_all(controller);}
    dprintf(report,"{\"controllerPid\":%d,\"normalExit\":%s,\"terminationSignal\":%d,\"injectedCoreExec\":%s,\"injectedResolverStep\":%s,\"phaseKilled\":%s,\"timedOut\":%s}\n",controller,normal<0?"null":({static char b[16];snprintf(b,sizeof(b),"%d",normal);b;}),signum,(deny&&injected)?"true":"false",((partial||restore)&&injected)?"true":"false",killed?"true":"false",timedout?"true":"false");
    fsync(report);close(report);
    return timedout?124:killed?137:normal>=0?normal:128+signum;
}
