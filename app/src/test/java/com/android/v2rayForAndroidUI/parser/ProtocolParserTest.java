package com.android.v2rayForAndroidUI.parser;

import com.android.v2rayForAndroidUI.model.OutboundObject;
import com.google.gson.Gson;

import org.junit.Test;

public class ProtocolParserTest {
    @Test
    public void testVLESS() {
        VLESSConfigParser parser = new VLESSConfigParser();

        OutboundObject outboundObject = parser.parseOutbound("vless://dc503d2f-9028-480f-9ebb-5bd46cfc969b@face.woxiangbaofu.click:443?encryption=none&security=tls&type=ws&host=face.woxiangbaofu.click&path=%2Fdc503d2f-9028-480f-9ebb-5bd46cfc969b#233boy-ws-face.woxiangbaofu.click");
        Gson gson = new Gson();
        String json = gson.toJson(outboundObject);
        System.out.println(json);
    }

    @Test
    public void testVMES() {
        VMESSConfigParser parser = new VMESSConfigParser();

        parser.parse("vmess://ew0KICAidiI6ICIyIiwNCiAgInBzIjogIjIzM2JveS10Y3AtNjcuMjMwLjE3Mi4yNDkiLA0KICAiYWRkIjogIjY3LjIzMC4xNzIuMjQ5IiwNCiAgInBvcnQiOiAiMTg4ODMiLA0KICAiaWQiOiAiN2U3Y2Q0OTktNjE0MS00ZGFlLWEzOTEtNWI1N2E5MjgzMThkIiwNCiAgImFpZCI6ICIwIiwNCiAgInNjeSI6ICJhdXRvIiwNCiAgIm5ldCI6ICJ0Y3AiLA0KICAidHlwZSI6ICJodHRwIiwNCiAgImhvc3QiOiAiIiwNCiAgInBhdGgiOiAiIiwNCiAgInRscyI6ICIiLA0KICAic25pIjogIiIsDQogICJhbHBuIjogIiIsDQogICJmcCI6ICIiDQp9");
        //parser.parse("vmess://eyJ2IjoyLCJwcyI6IjIzM2JveS13cy1mYWNlLndveGlhbmdiYW9mdS5jbGljayIsImFkZCI6ImZhY2Uud294aWFuZ2Jhb2Z1LmNsaWNrIiwicG9ydCI6IjQ0MyIsImlkIjoiZWQ5MzQzYzUtZTg3MC00ZTFiLWE1MTYtNGQzYzAxMjhkYmMwIiwiYWlkIjoiMCIsIm5ldCI6IndzIiwiaG9zdCI6ImZhY2Uud294aWFuZ2Jhb2Z1LmNsaWNrIiwicGF0aCI6Ii9lZDkzNDNjNS1lODcwLTRlMWItYTUxNi00ZDNjMDEyOGRiYzAiLCJ0bHMiOiJ0bHMifQ==");
    }


    @Test
    public void testTrojan() {
        TrojanConfigParser parser = new TrojanConfigParser();
        parser.parseOutbound("trojan://55411e44-8f8a-43ce-8f79-2eb9e188bdb9@tr0jan.woxiangbaofu.click:443?encryption=none&security=tls&type=ws&host=tr0jan.woxiangbaofu.click&path=%2F55411e44-8f8a-43ce-8f79-2eb9e188bdb9#233boy-ws-tr0jan.woxiangbaofu.click");
    }
}
