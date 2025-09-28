package com.android.xrayfa;

import android.app.Application;
import com.android.xrayfa.di.XrayFAComponent;


public class XrayFAApplication extends Application {

    private static final String TAG = "V2rayFAApplication";

    private XrayFAComponent rootComponent;

    private ContextAvailableCallback contextAvailableCallback;


    public void setContextAvailableCallback(ContextAvailableCallback contextAvailableCallback) {
        this.contextAvailableCallback = contextAvailableCallback;
    }

    public XrayFAComponent getRootComponent() {
        return rootComponent;
    }


    @Override
    public void onCreate() {
        super.onCreate();
        contextAvailableCallback.onContextAvailable(getApplicationContext());
    }

}
