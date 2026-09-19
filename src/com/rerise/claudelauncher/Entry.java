package com.rerise.claudelauncher;

public class Entry {
    public String id;
    public String name;
    public String url;
    public String icon;
    public int color;
    /** 旧バージョンの「必ずブラウザで開く」。pkg が空のときだけ効く */
    public boolean browser;
    /** 開き先アプリのパッケージ名。空なら Android に任せる */
    public String pkg;

    public Entry(String id, String name, String url, String icon, int color, boolean browser) {
        this(id, name, url, icon, color, browser, "");
    }

    public Entry(String id, String name, String url, String icon, int color, boolean browser, String pkg) {
        this.id = id;
        this.name = name;
        this.url = url;
        this.icon = icon;
        this.color = color;
        this.browser = browser;
        this.pkg = pkg == null ? "" : pkg;
    }
}
