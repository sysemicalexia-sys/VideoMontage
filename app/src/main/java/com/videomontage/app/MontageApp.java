package com.videomontage.app;

import android.app.Application;

public final class MontageApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        // Native engine initializes lazily on first surface attach; nothing
        // heavy belongs in Application.onCreate.
    }
}
