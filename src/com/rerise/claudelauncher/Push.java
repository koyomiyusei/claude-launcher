package com.rerise.claudelauncher;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * プッシュ通知（FCM）の準備。
 * Firebase の設定値はアプリに焼き込まず、連携先のサーバー（右腕メモの置き場）から受け取る。
 * こうしておくと、公開リポジトリに設定ファイルを置かずに済む。
 */
public class Push {

    private static final String PREF = "claude_talks";
    private static final String KEY_CONFIG = "fcm_config";
    private static final String KEY_STATE = "fcm_state";

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    /** いまの状態を人が読める1行で（メニューに出す） */
    public static String state(Context c) {
        return prefs(c).getString(KEY_STATE, "未設定");
    }

    private static void setState(Context c, String s) {
        prefs(c).edit().putString(KEY_STATE, s).apply();
    }

    /** 前に受け取った設定があれば、それで Firebase を起こす（通信なし。起動時に呼ぶ） */
    public static boolean initFromCache(Context c) {
        try {
            if (!FirebaseApp.getApps(c).isEmpty()) return true;
            String raw = prefs(c).getString(KEY_CONFIG, "");
            if (raw.isEmpty()) return false;
            JSONObject o = new JSONObject(raw);
            FirebaseOptions opt = new FirebaseOptions.Builder()
                    .setApiKey(o.getString("apiKey"))
                    .setApplicationId(o.getString("appId"))
                    .setProjectId(o.getString("projectId"))
                    .setGcmSenderId(o.getString("senderId"))
                    .build();
            FirebaseApp.initializeApp(c.getApplicationContext(), opt);
            return true;
        } catch (Exception e) {
            setState(c, "初期化に失敗: " + e.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * サーバー連携が済んでいれば、設定を取り寄せて宛先を登録する。
     * 画面を開くたびに呼んでよい（通信するので別スレッドで動く）。
     */
    public static void ensure(Context context) {
        final Context c = context.getApplicationContext();
        if (!TalkStore.hasServer(c)) return;
        new Thread(new Runnable() {
            public void run() {
                try {
                    if (prefs(c).getString(KEY_CONFIG, "").isEmpty()) {
                        String cfg = http(c, "GET", "/v1/fcm/config", null);
                        if (cfg == null) {
                            setState(c, "サーバー側が未設定（15分おきの確認で届きます）");
                            return;
                        }
                        new JSONObject(cfg).getString("appId"); // 形の確認
                        prefs(c).edit().putString(KEY_CONFIG, cfg).apply();
                    }
                    if (!initFromCache(c)) return;
                    FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
                        if (!task.isSuccessful() || task.getResult() == null) {
                            Exception e = task.getException();
                            setState(c, "宛先を取れませんでした: " + (e == null ? "?" : e.getMessage()));
                            return;
                        }
                        uploadToken(c, task.getResult());
                    });
                } catch (Exception e) {
                    setState(c, "準備に失敗: " + e.getClass().getSimpleName());
                }
            }
        }).start();
    }

    /** 端末の宛先をサーバーに覚えさせる */
    public static void uploadToken(Context context, final String token) {
        final Context c = context.getApplicationContext();
        if (token == null || token.isEmpty() || !TalkStore.hasServer(c)) return;
        new Thread(new Runnable() {
            public void run() {
                try {
                    JSONObject body = new JSONObject();
                    body.put("token", token);
                    String r = http(c, "POST", "/v1/fcm/token", body.toString());
                    setState(c, r != null ? "有効（すぐ届きます）" : "宛先の登録に失敗（あとで自動でやり直します）");
                } catch (Exception e) {
                    setState(c, "宛先の登録に失敗: " + e.getClass().getSimpleName());
                }
            }
        }).start();
    }

    /** 連携を外したとき用。次に連携したら設定を取り直す */
    public static void forget(Context c) {
        prefs(c).edit().remove(KEY_CONFIG).putString(KEY_STATE, "未設定").apply();
    }

    private static String http(Context c, String method, String path, String body) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(TalkStore.serverUrl(c) + path).openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestMethod(method);
            conn.setRequestProperty("Authorization", "Bearer " + TalkStore.serverToken(c));
            if (body != null) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                OutputStream os = conn.getOutputStream();
                os.write(body.getBytes("UTF-8"));
                os.close();
            }
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
}
