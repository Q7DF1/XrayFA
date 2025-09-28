package com.android.xrayfa.parser;

import com.android.xrayfa.model.OutboundObject;
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

        parser.parseOutbound("vmess://eyJ2IjoyLCJwcyI6IjIzM2JveS1ncnBjLXJwYy53b3hpYW5nYmFvZnUuY2xpY2siLCJhZGQiOiJycGMud294aWFuZ2Jhb2Z1LmNsaWNrIiwicG9ydCI6IjQ0MyIsImlkIjoiNmUyNDJlODItOWI5NS00NWI4LWI2MDctZmVmNTkyMGMyOThmIiwiYWlkIjoiMCIsIm5ldCI6ImdycGMiLCJob3N0IjoicnBjLndveGlhbmdiYW9mdS5jbGljayIsInBhdGgiOiI2ZTI0MmU4Mi05Yjk1LTQ1YjgtYjYwNy1mZWY1OTIwYzI5OGYiLCJ0bHMiOiJ0bHMifQ==");
    }


    @Test
    public void testTrojan() {
        TrojanConfigParser parser = new TrojanConfigParser();
        parser.parseOutbound("trojan://55411e44-8f8a-43ce-8f79-2eb9e188bdb9@tr0jan.woxiangbaofu.click:443?encryption=none&security=tls&type=ws&host=tr0jan.woxiangbaofu.click&path=%2F55411e44-8f8a-43ce-8f79-2eb9e188bdb9#233boy-ws-tr0jan.woxiangbaofu.click");
    }

    @Test
    public void testSS() {
        ShadowSocksConfigParser parser = new ShadowSocksConfigParser();
        ShadowSocksConfigParser.ShadowSocksConfig shadowSocksConfig = parser.parseLink("ss://YWVzLTI1Ni1nY206MTIzNDU2@67.230.172.249:18886#233boy-ss-67.230.172.249");
        System.out.println(shadowSocksConfig);
    }
}
