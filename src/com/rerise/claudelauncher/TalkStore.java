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
                arr.put(o);
            }
        } catch (Exception ignored) {
        }
        prefs(c).edit().putString(KEY_TALKS, arr.toString()).apply();
    }
}
