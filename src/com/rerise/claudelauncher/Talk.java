package com.rerise.claudelauncher;

/** トーク画面の1行（＝Claudeのセッション1つ） */
public class Talk {
    public String id;
    public String name;
    public String url;
    /** 所属タブの名前 */
    public String tab;
    /** 最後の一言（一覧に薄く出す） */
    public String last;
    /** 最後に動きがあった時刻（ミリ秒） */
    public long time;
    /** 返事が来ている（緑の丸） */
    public boolean unread;
    public boolean pinned;
    public int color;
    /** 開き先アプリのパッケージ名。空なら Android に任せる */
    public String pkg;

    public Talk() {
        id = Store.newId();
        name = "";
        url = "";
        tab = "";
        last = "";
        time = System.currentTimeMillis();
        color = Store.PALETTE[0];
        pkg = "";
    }
}
