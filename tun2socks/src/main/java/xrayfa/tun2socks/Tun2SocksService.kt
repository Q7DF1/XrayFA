package xrayfa.tun2socks

interface Tun2SocksService {


    suspend fun startTun2Socks(fd: Int)

    suspend fun stopTun2Socks()

}