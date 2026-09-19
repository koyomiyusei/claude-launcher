package com.rerise.claudelauncher;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class Store {

    private static final String PREF = "claude_launcher";
    private static final String KEY = "entries";
    private static final String KEY_SEEDED = "seeded";

    public static final int[] PALETTE = {
            0xFFD97757, // Claude orange
            0xFF3B82F6, // blue
            0xFF10B981, // green
            0xFF8B5CF6, // purple
            0xFFF59E0B, // amber
            0xFFEC4899, // pink
            0xFF14B8A6, // teal
            0xFF64748B  // slate
    };

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static List<Entry> load(Context c) {
        List<Entry> list = new ArrayList<Entry>();
        SharedPreferences p = prefs(c);
        if (!p.getBoolean(KEY_SEEDED, false)) {
            p.edit().putBoolean(KEY_SEEDED, true).apply();
            list.add(new Entry(newId(), "プロジェクト一覧", "https://claude.ai/projects", "P", PALETTE[0], false));
            list.add(new Entry(newId(), "チャット履歴", "https://claude.ai/recents", "履", PALETTE[1], false));
            list.add(new Entry(newId(), "新規チャット", "https://claude.ai/new", "＋", PALETTE[2], false));
            save(c, list);
            return list;
        }
        String raw = p.getString(KEY, "[]");
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                list.add(new Entry(
                        o.optString("id", newId()),
                        o.optString("name", ""),
                        o.optString("url", ""),
                        o.optString("icon", ""),
                        o.optInt("color", PALETTE[0]),
                        o.optBoolean("browser", false),
                        o.optString("pkg", "")));
            }
        } catch (Exception e) {
            // 壊れていたら空から再開する
        }
        return list;
    }

    public static void save(Context c, List<Entry> list) {
        JSONArray arr = new JSONArray();
        try {
            for (Entry e : list) {
                JSONObject o = new JSONObject();
                o.put("id", e.id);
                o.put("name", e.name);
                o.put("url", e.url);
                o.put("icon", e.icon);
                o.put("color", e.color);
                o.put("browser", e.browser);
                o.put("pkg", e.pkg == null ? "" : e.pkg);
                arr.put(o);
            }
        } catch (Exception ignored) {
        }
        prefs(c).edit().putString(KEY, arr.toString()).apply();
    }

    public static String newId() {
        return Long.toString(System.currentTimeMillis(), 36)
                + Integer.toString((int) (Math.random() * 46655), 36);
    }
}
