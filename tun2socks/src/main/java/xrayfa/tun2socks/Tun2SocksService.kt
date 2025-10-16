package xrayfa.tun2socks

interface Tun2SocksService {


    fun startTun2Socks(fd: Int)

    fun stopTun2Socks()
}