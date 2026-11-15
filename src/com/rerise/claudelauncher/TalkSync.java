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
                fresh.add(t);
            }
            changed = true;
        }
        if (tabsChanged) TalkStore.saveTabs(c, tabs);
        if (changed) TalkStore.save(c, talks);
        return fresh;
    }

    /** 定期ジョブ用。取ってきて混ぜ、新しく返事が来たものを通知する */
    public static void syncAndNotify(Context c) {
        JSONArray items = fetch(c);
        if (items == null) return;
        for (Talk t : merge(c, items)) Notices.notifyTalk(c, t);
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
