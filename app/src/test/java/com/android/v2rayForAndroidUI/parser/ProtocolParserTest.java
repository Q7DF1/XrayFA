package com.android.v2rayForAndroidUI.parser;

import com.android.v2rayForAndroidUI.model.OutboundObject;
import com.google.gson.Gson;

import org.junit.Test;

public class ProtocolParserTest {
    @Test
    public void testVLESS() {
        VLESSConfigParser parser = new VLESSConfigParser();

        OutboundObject outboundObject = parser.parseOutbound("vless://bc313a85-45dd-4904-80dc-37496b18e222@67.230.172.249:18880?encryption=none&flow=xtls-rprx-vision&security=reality&sni=www.paypal.com&fp=chrome&pbk=1CgDPWbxKfcyOa91dLnRxDZ3EuaEbU0GwFnkTIg2XWc&type=tcp&headerType=none#233boy-tcp-67.230.172.249");
        Gson gson = new Gson();
        String json = gson.toJson(outboundObject);
        System.out.println(json);
    }

    @Test
    public void testVMES() {
        VMESSConfigParser parser = new VMESSConfigParser();

        parser.parse("vmess://ew0KICAidiI6ICIyIiwNCiAgInBzIjogIjIzM2JveS10Y3AtNjcuMjMwLjE3Mi4yNDkiLA0KICAiYWRkIjogIjY3LjIzMC4xNzIuMjQ5IiwNCiAgInBvcnQiOiAiMTg4ODMiLA0KICAiaWQiOiAiN2U3Y2Q0OTktNjE0MS00ZGFlLWEzOTEtNWI1N2E5MjgzMThkIiwNCiAgImFpZCI6ICIwIiwNCiAgInNjeSI6ICJhdXRvIiwNCiAgIm5ldCI6ICJ0Y3AiLA0KICAidHlwZSI6ICJodHRwIiwNCiAgImhvc3QiOiAiIiwNCiAgInBhdGgiOiAiIiwNCiAgInRscyI6ICIiLA0KICAic25pIjogIiIsDQogICJhbHBuIjogIiIsDQogICJmcCI6ICIiDQp9");
    }
}
