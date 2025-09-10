package hev.htproxy.utils;



import java.util.Set;
import java.util.HashSet;
import android.content.Context;
import android.content.SharedPreferences;

public class NetPreferences {
    public static final String PREFS_NAME = "SocksPrefs";
    public static final String SOCKS_ADDR = "SocksAddr";
    public static final String SOCKS_PORT = "SocksPort";
    public static final String SOCKS_USER = "SocksUser";
    public static final String SOCKS_PASS = "SocksPass";
    public static final String DNS_IPV4 = "DnsIpv4";
    public static final String DNS_IPV6 = "DnsIpv6";
    public static final String IPV4 = "Ipv4";
    public static final String IPV6 = "Ipv6";
    public static final String GLOBAL = "Global";
    public static final String UDP_IN_TCP = "UdpInTcp";
    public static final String REMOTE_DNS = "RemoteDNS";
    public static final String APPS = "Apps";
    public static final String ENABLE = "Enable";

    private SharedPreferences prefs;

    public NetPreferences(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_MULTI_PROCESS);
    }

    public String getSocksAddress() {
        return prefs.getString(SOCKS_ADDR, "127.0.0.1");
    }

    public void setSocksAddress(String addr) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(SOCKS_ADDR, addr);
        editor.apply();
    }

    public int getSocksPort() {
        return prefs.getInt(SOCKS_PORT, 10808);
    }

    public void setSocksPort(int port) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(SOCKS_PORT, port);
        editor.apply();
    }

    public String getSocksUsername() {
        return prefs.getString(SOCKS_USER, "");
    }

    public void setSocksUsername(String user) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(SOCKS_USER, user);
        editor.apply();
    }

    public String getSocksPassword() {
        return prefs.getString(SOCKS_PASS, "");
    }

    public void setSocksPassword(String pass) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(SOCKS_PASS, pass);
        editor.apply();
    }

    public String getDnsIpv4() {
        return prefs.getString(DNS_IPV4, "8.8.8.8");
    }

    public void setDnsIpv4(String addr) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(DNS_IPV4, addr);
        editor.apply();
    }

    public String getDnsIpv6() {
        return prefs.getString(DNS_IPV6, "2001:4860:4860::8888");
    }

    public void setDnsIpv6(String addr) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(DNS_IPV6, addr);
        editor.apply();
    }

    public String getMappedDns() {
        return "8.8.8.8";
    }

    public boolean getUdpInTcp() {
        return prefs.getBoolean(UDP_IN_TCP, false);
    }

    public void setUdpInTcp(boolean enable) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(UDP_IN_TCP, enable);
        editor.apply();
    }

    public boolean getRemoteDns() {
        return prefs.getBoolean(REMOTE_DNS, true);
    }

    public void setRemoteDns(boolean enable) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(REMOTE_DNS, enable);
        editor.apply();
    }

    public boolean getIpv4() {
        return prefs.getBoolean(IPV4, true);
    }

    public void setIpv4(boolean enable) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(IPV4, enable);
        editor.apply();
    }

    public boolean getIpv6() {
        return prefs.getBoolean(IPV6, true);
    }

    public void setIpv6(boolean enable) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(IPV6, enable);
        editor.apply();
    }

    public boolean getGlobal() {
        return prefs.getBoolean(GLOBAL, false);
    }

    public void setGlobal(boolean enable) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(GLOBAL, enable);
        editor.commit();
    }

    public Set<String> getApps() {
        return prefs.getStringSet(APPS, new HashSet<String>());
    }

    public void setApps(Set<String> apps) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putStringSet(APPS, apps);
        editor.commit();
    }

    public boolean getEnable() {
        return prefs.getBoolean(ENABLE, false);
    }

    public void setEnable(boolean enable) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(ENABLE, enable);
        editor.commit();
    }

    public int getTunnelMtu() {
        return 8500;
    }

    public String getTunnelIpv4Address() {
        return "10.0.0.2";
    }

    public int getTunnelIpv4Prefix() {
        return 32;
    }

    public String getTunnelIpv6Address() {
        return "fc00::1";
    }

    public int getTunnelIpv6Prefix() {
        return 128;
    }

    public int getTaskStackSize() {
        return 81920;
    }
}
