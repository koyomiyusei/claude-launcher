package com.rerise.claudelauncher;

import android.content.Context;
import android.content.pm.ResolveInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * 右腕コネクタ（uwan-memo）に預けてあるトークを取ってきて、手元の一覧に混ぜる。
 * Claude が talk_add / talk_update を呼ぶと、ここを通ってスマホに届く。
 * 手元だけのもの（ピン留め・色・開き先）はサーバーに触らせない。
 */
public class TalkSync {

    private static final String CLAUDE_APP = "com.anthropic.claude";

    /** サーバーから一覧を取る。未設定・失敗は null */
    public static JSONArray fetch(Context c) {
        if (!TalkStore.hasServer(c)) return null;
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(TalkStore.serverUrl(c) + "/v1/board?kind=talk").openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("Authorization", "Bearer " + TalkStore.serverToken(c));
            if (conn.getResponseCode() != 200) return null;
            StringBuilder sb = new StringBuilder();
            BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();
            return new JSONObject(sb.toString()).optJSONArray("items");
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** 取ってきた一覧を手元に混ぜて保存する。戻り値は「新しく緑の丸が付いたトーク」 */
    public static List<Talk> merge(Context c, JSONArray items) {
        List<Talk> fresh = new ArrayList<Talk>();
        if (items == null) return fresh;
        List<Talk> talks = TalkStore.load(c);
        List<String> tabs = TalkStore.loadTabs(c);
        List<String> dismissed = TalkStore.dismissed(c);
        boolean changed = false, tabsChanged = false;

        for (int i = 0; i < items.length(); i++) {
            JSONObject o = items.optJSONObject(i);
            if (o == null) continue;
            String sid = o.optString("id", "");
            if (sid.isEmpty() || dismissed.contains(sid)) continue;
            long updated = o.optLong("updated", 0);
            String url = o.optString("url", "");

            Talk t = null;
            for (Talk x : talks) {
                if (sid.equals(x.sid)) { t = x; break; }
            }
            if (t == null && !url.isEmpty()) {
                // 手で足してあった同じURLのトークは、それを引き継ぐ
                for (Talk x : talks) {
                    if (x.sid.isEmpty() && url.equals(x.url)) { t = x; t.sid = sid; break; }
                }
            }
            boolean isNew = t == null;
            if (isNew) {
                t = new Talk();
                t.sid = sid;
                t.color = Store.PALETTE[talks.size() % Store.PALETTE.length];
                t.pkg = defaultPkg(c);
                t.tab = tabs.get(0);
                // 自分で並べているときは、新入りを一番上に置く
                long min = 0;
                for (Talk x : talks) min = Math.min(min, x.order);
                t.order = min - 1;
                talks.add(t);
            } else if (updated <= t.seen) {
                continue;
            }

            String name = o.optString("name", "");
            if (!name.isEmpty()) t.name = name;
            if (!url.isEmpty()) t.url = url;
            String tab = o.optString("tab", "").trim();
            if (!tab.isEmpty()) {
                if (!tabs.contains(tab)) { tabs.add(tab); tabsChanged = true; }
                t.tab = tab;
            }
            if (o.has("last")) t.last = o.optString("last", "");
            t.time = updated > 0 ? updated : System.currentTimeMillis();
            t.seen = updated;
            if (o.optBoolean("unread", false)) {
                t.unread = true;
                t.ask = "ask".equals(o.optString("state", ""));
                fresh.add(t);
            }
            changed = true;
        }
        if (tabsChanged) TalkStore.saveTabs(c, tabs);
        if (changed) TalkStore.save(c, talks);
        return fresh;
    }

    /** 定期ジョブ・プッシュ受信用。取ってきて混ぜ、新しく返事が来たものを通知する。通信できなければ false */
    public static boolean syncAndNotify(Context c) {
        JSONArray items = fetch(c);
        if (items == null) return false;
        for (Talk t : merge(c, items)) Notices.notifyTalk(c, t);
        return true;
    }

    /**
     * プッシュに載ってきた中身（名前・最後の一言・状態）をそのまま手元に反映し、通知用のトークを返す。
     * 通信しないので、端末が眠っていてもすぐ通知を出せる。細かい所はあとの同期で揃う。
     */
    public static Talk applyPush(Context c, java.util.Map<String, String> d) {
        String sid = d.get("id");
        if (sid == null || sid.isEmpty()) return null;
        boolean ask = "ask".equals(d.get("state"));
        String last = d.get("last") == null ? "" : d.get("last");
        List<Talk> talks = TalkStore.load(c);
        for (Talk t : talks) {
            if (!sid.equals(t.sid)) continue;
            t.unread = true;
            t.ask = ask;
            if (!last.isEmpty()) t.last = last;
            t.time = System.currentTimeMillis();
            TalkStore.save(c, talks);
            return t;
        }
        // まだ手元に無いトーク（増えた直後）。通知だけ出して、一覧への追加は同期に任せる
        Talk t = new Talk();
        t.id = "push-" + sid;
        t.name = d.get("name") == null ? "Claude" : d.get("name");
        t.url = d.get("url") == null ? "" : d.get("url");
        t.last = last;
        t.ask = ask;
        return t;
    }

    /** 同期するが通知は出さない（プッシュで通知を出したあと用） */
    public static void syncQuiet(Context c) {
        JSONArray items = fetch(c);
        if (items != null) merge(c, items);
    }

    /**
     * プッシュは届いたのにサーバーへ取りに行けなかったとき用。
     * 手元の一覧から相手を探して、とりあえず「返事が来ています」と知らせる（文面は次の同期で入る）。
     */
    public static void notifyWithoutSync(Context c, String sid) {
        if (sid == null || sid.isEmpty()) return;
        List<Talk> talks = TalkStore.load(c);
        for (Talk t : talks) {
            if (!sid.equals(t.sid)) continue;
            t.unread = true;
            t.time = System.currentTimeMillis();
            TalkStore.save(c, talks);
            Talk shown = new Talk();
            shown.id = t.id;
            shown.name = t.name;
            shown.url = t.url;
            shown.pkg = t.pkg;
            shown.last = "返事が来ています";
            Notices.notifyTalk(c, shown);
            return;
        }
    }

    /** スマホ側で消したトークを、サーバーからも外す（失敗しても手元の控えで復活は防ぐ） */
    public static void removeRemote(final Context c, final String sid) {
        if (sid == null || sid.isEmpty() || !TalkStore.hasServer(c)) return;
        final Context app = c.getApplicationContext();
        new Thread(new Runnable() {
            public void run() {
                HttpURLConnection conn = null;
                try {
                    JSONObject body = new JSONObject();
                    body.put("kind", "talk");
                    body.put("remove", new JSONArray().put(sid));
                    conn = (HttpURLConnection) new URL(TalkStore.serverUrl(app) + "/v1/board").openConnection();
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(10000);
                    conn.setRequestMethod("POST");
                    conn.setDoOutput(true);
                    conn.setRequestProperty("Authorization", "Bearer " + TalkStore.serverToken(app));
                    conn.setRequestProperty("Content-Type", "application/json");
                    OutputStream os = conn.getOutputStream();
                    os.write(body.toString().getBytes("UTF-8"));
                    os.close();
                    conn.getResponseCode();
                } catch (Exception ignored) {
                } finally {
                    if (conn != null) conn.disconnect();
                }
            }
        }).start();
    }

    private static String defaultPkg(Context c) {
        for (ResolveInfo ri : Handlers.forClaude(c)) {
            if (CLAUDE_APP.equals(ri.activityInfo.packageName)) return CLAUDE_APP;
        }
        return "";
    }
}
