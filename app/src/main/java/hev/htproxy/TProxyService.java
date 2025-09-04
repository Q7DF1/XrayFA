package hev.htproxy;

import android.content.Context;
import android.util.Log;

import com.android.v2rayForAndroidUI.di.qualifier.Application;
import com.android.v2rayForAndroidUI.di.qualifier.Background;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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

    @Inject
    public TProxyService(
            @Application Context context,
            @Background Executor bgExecutor
    ) {
        this.context = context;
        this.bgExecutor = bgExecutor;
    }

    public static native void TProxyStartService(String configPath, int fd);

    public static native void TProxyStopService();

    public static native long[] TProxyGetStats();


    @Override
    public void startTun2Socks(int fd) {
        bgExecutor.execute(()-> {
            String file_config = configure();
            TProxyStartService(file_config,fd);
        });
    }

    @Override
    public void stopTun2Socks() {
        bgExecutor.execute(TProxyService::TProxyStopService);
    }


    private String configure() {
        File configFile = new File(context.getCacheDir(), "tun2socks_config.yaml");
        try (InputStream inputStream = context.getAssets().open("tun2socks.yaml")) {
            OutputStream outputStream = new FileOutputStream(configFile);
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
            outputStream.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return configFile.getAbsolutePath();
    }

}
