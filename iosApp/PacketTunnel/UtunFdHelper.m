#import "UtunFdHelper.h"
#import "hev-main.h"

int XrayFAHevSocks5TunnelRun(const char *configPath, int tunFd) {
    if (configPath == NULL || tunFd < 0) {
        return -1;
    }
    return hev_socks5_tunnel_main(configPath, tunFd);
}

void XrayFAHevSocks5TunnelQuit(void) {
    hev_socks5_tunnel_quit();
}
