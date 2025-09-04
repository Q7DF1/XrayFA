package com.android.v2rayForAndroidUI;

import android.app.Application;
import android.app.Service;
import android.util.Log;

import com.android.v2rayForAndroidUI.di.DaggerV2rayComponent;
import com.android.v2rayForAndroidUI.di.V2rayComponent;

import org.jetbrains.annotations.NotNull;

import java.util.Map;

import javax.inject.Provider;

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
