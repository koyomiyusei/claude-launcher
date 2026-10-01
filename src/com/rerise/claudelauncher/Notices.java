package com.rerise.claudelauncher;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * GitHub 上の notices.json を見て、未読のお知らせを通知に出す。
 * Claude 側が作業完了などを書き込む想定。
 */
public class Notices {

    public static final String NOTICES_URL =
            "https://raw.githubusercontent.com/koyomiyusei/claude-launcher/main/notices.json";

    private static final String PREF = "claude_launcher";
    private static final String KEY_SEEN = "seen_notice_ids";
    private static final String KEY_ENABLED = "notices_enabled";
    private static final String CHANNEL = "claude_notices";
    private static final int MAX_SEEN = 200;

    public static boolean enabled(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true);
    }

    public static void setEnabled(Context c, boolean on) {
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, on).apply();
    }

    /** 取得して未読があれば通知。戻り値は新しく出した件数（-1 は通信失敗） */
    public static int checkAndNotify(Context c) {
        if (!enabled(c)) return 0;
        String body = fetch();
        if (body == null) return -1;

        List<String> seen = loadSeen(c);
        int shown = 0;
        try {
            JSONObject root = new JSONObject(body);
            JSONArray arr = root.optJSONArray("notices");
            if (arr == null) return 0;
            ensureChannel(c);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String id = o.optString("id", "");
                if (id.isEmpty() || seen.contains(id)) continue;
                notifyOne(c, id, o.optString("title", "Claude"),
                        o.optString("body", ""), o.optString("url", ""));
                seen.add(id);
                shown++;
            }
        } catch (Exception e) {
            return -1;
        }
        saveSeen(c, seen);
        return shown;
    }

    private static String fetch() {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(NOTICES_URL).openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("Cache-Control", "no-cache");
            if (conn.getResponseCode() != 200) return null;
            StringBuilder sb = new StringBuilder();
            BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            r.close();
            return sb.toString();
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static void ensureChannel(Context c) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm == null || nm.getNotificationChannel(CHANNEL) != null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Claudeからのお知らせ",
                NotificationManager.IMPORTANCE_DEFAULT);
        ch.setDescription("作業の完了などの連絡");
        nm.createNotificationChannel(ch);
    }

    private static void notifyOne(Context c, String id, String title, String body, String url) {
        Intent open;
        if (url != null && !url.isEmpty()) {
            open = new Intent(c, OpenActivity.class);
            open.setAction(Intent.ACTION_VIEW);
            open.putExtra("url", url);
            open.putExtra("pkg", "");
        } else {
            open = new Intent(c, MainActivity.class);
        }
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(c, id.hashCode(), open, flags);

        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            b = new Notification.Builder(c, CHANNEL);
        } else {
            b = new Notification.Builder(c);
        }
        b.setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setContentIntent(pi);

        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(Math.abs(id.hashCode()), b.build());
    }

    private static List<String> loadSeen(Context c) {
        List<String> out = new ArrayList<String>();
        String raw = c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_SEEN, "");
        if (!raw.isEmpty()) {
            for (String s : raw.split(",")) if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    private static void saveSeen(Context c, List<String> seen) {
        while (seen.size() > MAX_SEEN) seen.remove(0);
        StringBuilder sb = new StringBuilder();
        for (String s : seen) {
            if (sb.length() > 0) sb.append(',');
            sb.append(s);
        }
        SharedPreferences p = c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        p.edit().putString(KEY_SEEN, sb.toString()).apply();
    }
}
