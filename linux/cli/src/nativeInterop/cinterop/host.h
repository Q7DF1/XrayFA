#define _GNU_SOURCE
#include <sys/types.h>
#include <sys/stat.h>
#include <sys/file.h>
#include <sys/wait.h>
#include <sys/prctl.h>
#include <fcntl.h>
#include <unistd.h>
#include <errno.h>
#include <signal.h>
#include <spawn.h>
#include <poll.h>
#include <time.h>
#include <pwd.h>
#include <stdlib.h>
#include <stdio.h>

static volatile sig_atomic_t xf_signal = 0;
static void xf_handler(int sig) { xf_signal = sig; }
static inline void xf_signals(void) {
    struct sigaction sa = {0}; sa.sa_handler = xf_handler;
    struct sigaction ignore = {0}; ignore.sa_handler = SIG_IGN; sigaction(SIGPIPE,&ignore,NULL);
    sigaction(SIGINT, &sa, NULL); sigaction(SIGTERM, &sa, NULL); sigaction(SIGHUP, &sa, NULL);
}
static inline int xf_interrupted(void) { return xf_signal; }
static inline long long xf_millis(void) {
    struct timespec ts; clock_gettime(CLOCK_MONOTONIC, &ts);
    return (long long)ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
}
static inline const char *xf_home(void) {
    struct passwd *pw = getpwuid(getuid()); return pw ? pw->pw_dir : NULL;
}
static inline int xf_lock(int fd) { return flock(fd, LOCK_EX); }
static inline int xf_owned(const char *path, int directory) {
    struct stat s;
    if (lstat(path, &s)) return errno == ENOENT ? 0 : -1;
    if (s.st_uid != getuid() || (directory ? !S_ISDIR(s.st_mode) : !S_ISREG(s.st_mode))) return -1;
    if (s.st_mode & 0077) return -1;
    return 1;
}
static inline int xf_profile_parent_usable(const char *home, unsigned int uid) {
    char path[4096]; struct stat s;
    if (snprintf(path,sizeof(path),"%s/.local",home) < 0 || stat(path,&s)) return errno == ENOENT;
    return S_ISDIR(s.st_mode) && s.st_uid == uid && !(s.st_mode & 0022);
}
static inline long long xf_size(const char *path) { struct stat s; return stat(path, &s) ? -1 : s.st_size; }
static inline unsigned long long xf_inode(const char *path) { struct stat s; return stat(path, &s) ? 0 : s.st_ino; }
static inline unsigned int xf_mode(const char *path) { struct stat s; return stat(path, &s) ? 0 : (unsigned int)(s.st_mode & 07777); }
static inline int xf_errno(void) { return errno; }
static inline int xf_spawn(const char *path, char *const argv[], char *const env[], int *out, int *err) {
    int a[2], b[2]; if (pipe2(a, O_CLOEXEC)) return -1;
    if (pipe2(b, O_CLOEXEC)) { close(a[0]); close(a[1]); return -1; }
    posix_spawn_file_actions_t f; posix_spawn_file_actions_init(&f);
    posix_spawn_file_actions_addopen(&f, 0, "/dev/null", O_RDONLY, 0);
    posix_spawn_file_actions_adddup2(&f, a[1], 1);
    posix_spawn_file_actions_adddup2(&f, b[1], 2);
    posix_spawn_file_actions_addclose(&f, a[0]); posix_spawn_file_actions_addclose(&f, b[0]);
    posix_spawnattr_t attr; posix_spawnattr_init(&attr);
    posix_spawnattr_setflags(&attr, POSIX_SPAWN_SETPGROUP); posix_spawnattr_setpgroup(&attr, 0);
    pid_t pid; int rc = posix_spawn(&pid, path, &f, &attr, argv, env);
    posix_spawnattr_destroy(&attr); posix_spawn_file_actions_destroy(&f);
    close(a[1]); close(b[1]);
    if (rc) { close(a[0]); close(b[0]); errno=rc; return -1; }
    fcntl(a[0], F_SETFL, O_NONBLOCK); fcntl(b[0], F_SETFL, O_NONBLOCK);
    *out=a[0]; *err=b[0]; return pid;
}
static inline int xf_wait(int pid, int *result) {
    int status; int rc=waitpid(pid,&status,WNOHANG);
    if (rc==pid) *result=WIFEXITED(status)?WEXITSTATUS(status):128+WTERMSIG(status);
    return rc;
}

/* Runtime caller identity comes from the numeric UID and NSS, never HOME/SUDO_USER. */
static inline const char *xf_uid_home(unsigned int uid) {
    struct passwd *pw = getpwuid(uid); return pw ? pw->pw_dir : NULL;
}
static inline int xf_fd_owned(int fd, unsigned int uid, int directory) {
    struct stat s;
    if (fstat(fd, &s)) return 0;
    return s.st_uid == uid && !(s.st_mode & 0077) &&
        (directory ? S_ISDIR(s.st_mode) : S_ISREG(s.st_mode));
}
static inline int xf_snapshot_open_path(const char *profile, unsigned int uid) {
    int fd = open(profile, O_RDONLY|O_DIRECTORY|O_NOFOLLOW|O_CLOEXEC);
    if (fd < 0) return -1;
    struct stat s;
    if (fstat(fd,&s) || s.st_uid != uid || (s.st_mode & 0077)) { close(fd); errno=EPERM; return -1; }
    int file=openat(fd,"nodes.json",O_RDONLY|O_NOFOLLOW|O_CLOEXEC|O_NONBLOCK);
    close(fd);
    if(file>=0 && !xf_fd_owned(file,uid,0)) {close(file);errno=EPERM;return -1;}
    return file;
}
/* Anchor each directory FD: final open cannot race a rename or follow a symlink. */
static inline int xf_snapshot_open(const char *home, unsigned int uid) {
    int fd = open(home, O_RDONLY|O_DIRECTORY|O_NOFOLLOW|O_CLOEXEC);
    if (fd < 0) return -1;
    struct stat s;
    if (fstat(fd,&s) || s.st_uid != uid || (s.st_mode & 0022)) { close(fd); errno=EPERM; return -1; }
    const char *parts[]={".local","share","xrayfa"};
    for (int i=0;i<3;i++) {
        int next=openat(fd,parts[i],O_RDONLY|O_DIRECTORY|O_NOFOLLOW|O_CLOEXEC);
        close(fd); if(next<0) return -1; fd=next;
        if(fstat(fd,&s) || s.st_uid!=uid || (s.st_mode & (i==2?0077:0022))) {
            close(fd); errno=EPERM; return -1;
        }
    }
    int file=openat(fd,"nodes.json",O_RDONLY|O_NOFOLLOW|O_CLOEXEC|O_NONBLOCK);
    close(fd);
    if(file>=0 && !xf_fd_owned(file,uid,0)) {close(file);errno=EPERM;return -1;}
    return file;
}

#include <sys/syscall.h>
#if defined(__x86_64__)
#ifndef SYS_pidfd_open
#define SYS_pidfd_open 434
#endif
#ifndef SYS_pidfd_send_signal
#define SYS_pidfd_send_signal 424
#endif
#endif
/* Only C/POSIX code executes between fork and exec. The child cannot execute
 * the core until the parent has durably recorded its PID/start-time identity. */
static inline int xf_spawn_gated(int executable_fd, char *const argv[], char *const env[],
                                  int *out, int *err, int *release, int die_with_parent) {
    int a[2],b[2],g[2];
    if(pipe2(a,O_CLOEXEC))return -1;
    if(pipe2(b,O_CLOEXEC)){close(a[0]);close(a[1]);return -1;}
    if(pipe2(g,O_CLOEXEC)){close(a[0]);close(a[1]);close(b[0]);close(b[1]);return -1;}
    pid_t parent=getpid();pid_t pid=fork();
    if(pid==0){
        if(die_with_parent && (prctl(PR_SET_PDEATHSIG,SIGKILL) || getppid()!=parent))_exit(125);
        close(a[0]);close(b[0]);close(g[1]);
        if(setpgid(0,0))_exit(126);
        int null=open("/dev/null",O_RDONLY);
        if(null<0 || dup2(null,0)<0 || dup2(a[1],1)<0 || dup2(b[1],2)<0)_exit(126);
        if(null>2)close(null);
        close(a[1]);close(b[1]);
        char token=0;ssize_t n;
        do{n=read(g[0],&token,1);}while(n<0 && errno==EINTR);
        close(g[0]);
        if(n!=1 || token!='X')_exit(125);
        syscall(SYS_execveat,executable_fd,"",argv,env,AT_EMPTY_PATH);
        _exit(127);
    }
    close(a[1]);close(b[1]);close(g[0]);
    if(pid<0){close(a[0]);close(b[0]);close(g[1]);return -1;}
    fcntl(a[0],F_SETFL,O_NONBLOCK);fcntl(b[0],F_SETFL,O_NONBLOCK);
    *out=a[0];*err=b[0];*release=g[1];return pid;
}
static inline int xf_try_lock(int fd){return flock(fd,LOCK_EX|LOCK_NB);}
static inline unsigned long long xf_fd_device(int fd){struct stat s;return fstat(fd,&s)?0:s.st_dev;}
static inline unsigned long long xf_fd_inode(int fd){struct stat s;return fstat(fd,&s)?0:s.st_ino;}
static inline int xf_trusted_core(int fd){struct stat s;return !fstat(fd,&s)&&S_ISREG(s.st_mode)&&s.st_uid==0&&!(s.st_mode&0022);}
/* A sudo-launched runtime may use the invoking user's own core. It must still
 * be a regular executable file and must not be writable by group/other; the
 * caller UID check prevents accepting an unrelated user's writable binary. */
static inline int xf_runtime_core(int fd,unsigned int caller_uid,int via_sudo){
    struct stat s;
    return !fstat(fd,&s)&&S_ISREG(s.st_mode)&&!(s.st_mode&0022)&&
        (s.st_uid==0 || (via_sudo && s.st_uid==caller_uid));
}
static inline int xf_pidfd_open(int pid){return syscall(SYS_pidfd_open,pid,0);}
static inline int xf_pidfd_signal(int fd,int sig){return syscall(SYS_pidfd_send_signal,fd,sig,NULL,0);}
static inline int xf_pidfd_exited(int fd){struct pollfd p={.fd=fd,.events=POLLIN};return poll(&p,1,0)>0;}
static inline int xf_process_exe_matches(int pid,unsigned long long dev,unsigned long long ino){
    char path[64];snprintf(path,sizeof(path),"/proc/%d/exe",pid);struct stat s;
    return !stat(path,&s)&&s.st_dev==dev&&s.st_ino==ino;
}
#if defined(__x86_64__) && !defined(SYS_getrandom)
#define SYS_getrandom 318
#endif
static inline int xf_random_bytes(unsigned char *out,int length){
    int total=0;while(total<length){ssize_t n=syscall(SYS_getrandom,out+total,length-total,0);
        if(n<0&&errno==EINTR)continue;if(n<=0)return -1;total+=(int)n;}return 0;
}
static inline int xf_nonblocking_stderr(void){int flags=fcntl(2,F_GETFL);return flags<0?-1:fcntl(2,F_SETFL,flags|O_NONBLOCK);}
static inline int xf_log_file(int fd){struct stat s;return !fstat(fd,&s)&&S_ISREG(s.st_mode)&&s.st_uid==getuid()&&(s.st_mode&0777)==0600;}
