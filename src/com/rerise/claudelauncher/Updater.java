package com.rerise.claudelauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * GitHub 上の latest.json を見て、新しいAPKがあれば知らせる。
 * ダウンロードはブラウザに投げる（インストールはAndroid標準の流れに任せる）。
 */
public class Updater {

    public static final String LATEST_URL =
            "https://raw.githubusercontent.com/koyomiyusei/claude-launcher/main/latest.json";

    private static final String PREF = "claude_launcher";
    private static final String KEY_LAST_CHECK = "last_update_check";
    private static final long AUTO_INTERVAL = 24L * 60 * 60 * 1000;

    public static int currentCode(Context c) {
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return pi.versionCode;
        } catch (Exception e) {
            return 0;
        }
    }

    public static String currentName(Context c) {
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            return pi.versionName == null ? "?" : pi.versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    /** 1日に1回だけ、黙って確認する。新しいものがあるときだけ声をかける */
    public static void autoCheck(Activity a) {
        SharedPreferences p = a.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        long last = p.getLong(KEY_LAST_CHECK, 0);
        long now = System.currentTimeMillis();
        if (now - last < AUTO_INTERVAL) return;
        p.edit().putLong(KEY_LAST_CHECK, now).apply();
        check(a, true);
    }

    public static void check(final Activity a, final boolean silent) {
        if (!silent) Toast.makeText(a, "確認しています…", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            public void run() {
                JSONObject obj = null;
                String err = null;
                HttpURLConnection conn = null;
                try {
                    conn = (HttpURLConnection) new URL(LATEST_URL).openConnection();
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(10000);
                    conn.setRequestProperty("Cache-Control", "no-cache");
                    int code = conn.getResponseCode();
                    if (code != 200) throw new Exception("HTTP " + code);
                    StringBuilder sb = new StringBuilder();
                    BufferedReader r = new BufferedReader(
                            new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    String line;
                    while ((line = r.readLine()) != null) sb.append(line);
                    r.close();
                    obj = new JSONObject(sb.toString());
                } catch (Exception e) {
                    err = e.getClass().getSimpleName() + ": " + e.getMessage();
                } finally {
                    if (conn != null) conn.disconnect();
                }
                final JSONObject result = obj;
                final String error = err;
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    public void run() {
                        present(a, result, error, silent);
                    }
                });
            }
        }).start();
    }

    private static void present(Activity a, JSONObject o, String error, boolean silent) {
        if (a.isFinishing() || a.isDestroyed()) return;

        if (o == null) {
            if (!silent) {
                new AlertDialog.Builder(a)
                        .setTitle("確認できませんでした")
                        .setMessage("通信に失敗しました。\n\n" + error)
                        .setPositiveButton("OK", null)
                        .show();
            }
            return;
        }

        int latest = o.optInt("versionCode", 0);
        String latestName = o.optString("versionName", "?");
        String apkUrl = o.optString("apkUrl", "");
        String notes = o.optString("notes", "");
        int current = currentCode(a);

        if (latest <= current) {
            if (!silent) {
                Toast.makeText(a, "最新です（" + currentName(a) + "）", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        String msg = "現在: " + currentName(a) + "\n新しい版: " + latestName;
        if (!notes.isEmpty()) msg += "\n\n" + notes;
        msg += "\n\nダウンロードするとブラウザに保存されます。通知をタップするとインストールできます。";

        final String url = apkUrl;
        new AlertDialog.Builder(a)
                .setTitle("アップデートがあります")
                .setMessage(msg)
                .setNegativeButton("あとで", null)
                .setPositiveButton("ダウンロード", (d, w) -> {
                    if (url.isEmpty()) {
                        Toast.makeText(a, "ダウンロード先が設定されていません", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    try {
                        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        i.addCategory(Intent.CATEGORY_BROWSABLE);
                        a.startActivity(i);
                    } catch (Exception e) {
                        Toast.makeText(a, "開けませんでした", Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }
}
