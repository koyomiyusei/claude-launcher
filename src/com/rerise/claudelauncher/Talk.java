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
    /** 丸の意味。false=作業が終わった（緑） / true=質問・承認待ちで止まっている（オレンジ） */
    public boolean ask;
    public boolean pinned;
    public int color;
    /** 開き先アプリのパッケージ名。空なら Android に任せる */
    public String pkg;
    /** サーバー（右腕コネクタ）側のid。手で足したトークは空 */
    public String sid = "";
    /** サーバー側の更新時刻のうち、ここまでは取り込み済み */
    public long seen = 0;
    /** 自分で並べるときの位置（小さいほど上） */
    public long order = 0;

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
