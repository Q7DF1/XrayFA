package com.android.v2rayForAndroidUI.utils;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import hev.htproxy.utils.NetPreferences;
import kotlin.text.Charsets;

public class Config {

    private static final String TAG = "utils/Config";

    public static File getConfigFile(Context context) {
        NetPreferences prefs = new NetPreferences(context);
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
            Log.i(TAG, "getConfigFile: e");
        }
        return tproxy_file;
    }


    public static String jsonToString(InputStream fis) {
        byte[] buffer = null;
        try {
            buffer = new byte[fis.available()];
            int read = fis.read(buffer);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return new String(buffer, Charsets.UTF_8);
        }

}
