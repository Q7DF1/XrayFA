package hev.htproxy;

import android.content.Context;
import android.util.Log;

import com.android.v2rayForAndroidUI.di.qualifier.Application;
import com.android.v2rayForAndroidUI.di.qualifier.Background;
import com.android.v2rayForAndroidUI.utils.NetPreferences;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.Executor;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class TProxyService implements Tun2SocksService{

    private static final String TAG = "TProxyService";

    static {
        System.loadLibrary("hev-socks5-tunnel");
    }
    private final Executor bgExecutor;

    private final Context context;
    private final NetPreferences prefs;

    @Inject
    public TProxyService(
            @Application Context context,
            @Background Executor bgExecutor,
            NetPreferences netPreferences
    ) {
        this.context = context;
        this.bgExecutor = bgExecutor;
        this.prefs = netPreferences;
    }

    public static native void TProxyStartService(String configPath, int fd);

    public static native void TProxyStopService();

    public static native long[] TProxyGetStats();


    @Override
    public void startTun2Socks(int fd) {
        bgExecutor.execute(()-> {
            String file_config = configure();
            try {
                TProxyStartService(file_config,fd);
            }catch (Exception e){
                Log.e(TAG, "startTun2Socks: "+ e.getMessage());
            }
        });
    }

    @Override
    public void stopTun2Socks() {
        bgExecutor.execute(TProxyService::TProxyStopService);
    }


    private String configure() {
        File tproxy_file = new File(context.getCacheDir(), "tproxy.conf");
        try {
            tproxy_file.createNewFile();
            FileOutputStream fos = new FileOutputStream(tproxy_file, false);

            String tproxy_conf = "misc:\n" +
                    "  task-stack-size: " + prefs.getTaskStackSize() + "\n" +
                    "tunnel:\n" +
                    "  mtu: " + prefs.getTunnelMtu() + "\n";

            tproxy_conf += "socks5:\n" +
                    "  port: " + prefs.getSocksPort() + "\n" +
                    "  address: '" + prefs.getSocksAddress() + "'\n" +
                    "  udp: '" + (prefs.getUdpInTcp() ? "tcp" : "udp") + "'\n";

            if (!prefs.getSocksUsername().isEmpty() &&
                    !prefs.getSocksPassword().isEmpty()) {
                tproxy_conf += "  username: '" + prefs.getSocksUsername() + "'\n";
                tproxy_conf += "  password: '" + prefs.getSocksPassword() + "'\n";
            }

            if (prefs.getRemoteDns()) {
                tproxy_conf += "mapdns:\n" +
                        "  address: " + prefs.getMappedDns() + "\n" +
                        "  port: 53\n" +
                        "  network: 240.0.0.0\n" +
                        "  netmask: 240.0.0.0\n" +
                        "  cache-size: 10000\n";
            }

            fos.write(tproxy_conf.getBytes());
            fos.close();
        } catch (IOException e) {
            throw new RuntimeException();
        }
        return tproxy_file.getAbsolutePath();
    }

}
