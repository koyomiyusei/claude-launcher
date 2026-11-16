package com.rerise.claudelauncher;

import android.app.Application;

/** アプリの起動時に必ず通る場所。プッシュ通知の受け口が動く前に Firebase を用意する */
public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Push.initFromCache(this);
    }
}
