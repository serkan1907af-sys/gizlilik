package com.tavlaoyunu.klasik;

import android.app.Application;

public class TavlaApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Prefs.init(this);
        Sfx.init(this);
        AdManager.init(this);
        ProManager.init(this);
    }
}
