package com.rerise.claudelauncher;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** トークとタブの保存。アイコン一覧（Store）とは別の入れ物にしてある */
public class TalkStore {

    private static final String PREF = "claude_talks";
    private static final String KEY_TALKS = "talks";
    private static final String KEY_TABS = "tabs";
    private static final String KEY_CURRENT = "current_tab";

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static List<String> loadTabs(Context c) {
        List<String> tabs = new ArrayList<String>();
        try {
            JSONArray arr = new JSONArray(prefs(c).getString(KEY_TABS, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                String s = arr.optString(i, "").trim();
                if (!s.isEmpty() && !tabs.contains(s)) tabs.add(s);
            }
        } catch (Exception ignored) {
        }
        if (tabs.isEmpty()) {
            tabs.add("プライベート");
            tabs.add("仕事");
        }
        return tabs;
    }

    public static void saveTabs(Context c, List<String> tabs) {
        JSONArray arr = new JSONArray();
        for (String t : tabs) arr.put(t);
        prefs(c).edit().putString(KEY_TABS, arr.toString()).apply();
    }

    public static String currentTab(Context c) {
        return prefs(c).getString(KEY_CURRENT, "");
    }

    public static void setCurrentTab(Context c, String tab) {
        prefs(c).edit().putString(KEY_CURRENT, tab).apply();
    }

    /** 並び順。false=新しい順（既定） / true=自分で並べる */
    public static boolean manualOrder(Context c) {
        return prefs(c).getBoolean("manual_order", false);
    }

    public static void setManualOrder(Context c, boolean on) {
        prefs(c).edit().putBoolean("manual_order", on).apply();
    }

    // ---- サーバー連携（右腕メモの置き場と同じ「住所 合言葉」の1行）----

    public static String serverUrl(Context c) {
        return prefs(c).getString("server_url", "");
    }

    public static String serverToken(Context c) {
        return prefs(c).getString("server_token", "");
    }

    public static boolean hasServer(Context c) {
        return !serverUrl(c).isEmpty() && !serverToken(c).isEmpty();
    }

    public static void setServer(Context c, String url, String token) {
        prefs(c).edit().putString("server_url", url).putString("server_token", token).apply();
    }

    /** スマホ側で消したサーバー由来のトーク。次の同期で復活させないための控え */
    public static List<String> dismissed(Context c) {
        List<String> out = new ArrayList<String>();
        try {
            JSONArray arr = new JSONArray(prefs(c).getString("dismissed", "[]"));
            for (int i = 0; i < arr.length(); i++) out.add(arr.optString(i, ""));
        } catch (Exception ignored) {
        }
        return out;
    }

    public static void addDismissed(Context c, String sid) {
        List<String> d = dismissed(c);
        if (d.contains(sid)) return;
        d.add(sid);
        while (d.size() > 300) d.remove(0);
        JSONArray arr = new JSONArray();
        for (String s : d) arr.put(s);
        prefs(c).edit().putString("dismissed", arr.toString()).apply();
    }

    public static List<Talk> load(Context c) {
        List<Talk> list = new ArrayList<Talk>();
        try {
            JSONArray arr = new JSONArray(prefs(c).getString(KEY_TALKS, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Talk t = new Talk();
                t.id = o.optString("id", t.id);
                t.name = o.optString("name", "");
                t.url = o.optString("url", "");
                t.tab = o.optString("tab", "");
                t.last = o.optString("last", "");
                t.time = o.optLong("time", System.currentTimeMillis());
                t.unread = o.optBoolean("unread", false);
                t.pinned = o.optBoolean("pinned", false);
                t.color = o.optInt("color", Store.PALETTE[0]);
                t.pkg = o.optString("pkg", "");
                t.sid = o.optString("sid", "");
                t.seen = o.optLong("seen", 0);
                t.order = o.optLong("order", 0);
                list.add(t);
            }
        } catch (Exception e) {
            // 壊れていたら空から再開する
        }
        return list;
    }

    public static void save(Context c, List<Talk> list) {
        JSONArray arr = new JSONArray();
        try {
            for (Talk t : list) {
                JSONObject o = new JSONObject();
                o.put("id", t.id);
                o.put("name", t.name);
                o.put("url", t.url);
                o.put("tab", t.tab);
                o.put("last", t.last == null ? "" : t.last);
                o.put("time", t.time);
                o.put("unread", t.unread);
                o.put("pinned", t.pinned);
                o.put("color", t.color);
                o.put("pkg", t.pkg == null ? "" : t.pkg);
                o.put("sid", t.sid == null ? "" : t.sid);
                o.put("seen", t.seen);
                o.put("order", t.order);
                arr.put(o);
            }
        } catch (Exception ignored) {
        }
        prefs(c).edit().putString(KEY_TALKS, arr.toString()).apply();
    }
}
