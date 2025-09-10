package com.android.v2rayForAndroidUI.parser;

import org.junit.Test;

public class ProtocolParserTest {
    @Test
    public void test() {
        VLESSConfigParser parser = new VLESSConfigParser();

        parser.getJsonConfigStringFromLink("vless://bc313a85-45dd-4904-80dc-37496b18e222@67.230.172.249:18880?encryption=none&flow=xtls-rprx-vision&security=reality&sni=www.paypal.com&fp=chrome&pbk=1CgDPWbxKfcyOa91dLnRxDZ3EuaEbU0GwFnkTIg2XWc&type=tcp&headerType=none#233boy-tcp-67.230.172.249");
    }
}
