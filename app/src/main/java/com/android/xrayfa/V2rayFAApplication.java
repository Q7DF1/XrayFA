package com.android.xrayfa;

import android.app.Application;
import com.android.xrayfa.di.V2rayComponent;


public class V2rayFAApplication extends Application {

    private static final String TAG = "V2rayFAApplication";

    private V2rayComponent rootComponent;

    private ContextAvailableCallback contextAvailableCallback;


    public void setContextAvailableCallback(ContextAvailableCallback contextAvailableCallback) {
        this.contextAvailableCallback = contextAvailableCallback;
    }

    public V2rayComponent getRootComponent() {
        return rootComponent;
    }


    @Override
    public void onCreate() {
        super.onCreate();
        contextAvailableCallback.onContextAvailable(getApplicationContext());
    }

}
