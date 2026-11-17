package com.rerise.claudelauncher;

import android.app.Activity;
import android.content.Context;

/** 画面の色（暗い／明るい／端末に合わせる）。各画面の onCreate の最初で apply を呼ぶ */
public class Theme {

    public static final int DARK = 0, LIGHT = 1, AUTO = 2;
    private static final String PREF = "claude_talks";
    private static final String KEY = "theme_mode";

    /** 既定は暗い（2026-11-16 本人の希望） */
    public static int mode(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getInt(KEY, DARK);
    }

    public static String label(Context c) {
        int m = mode(c);
        return m == DARK ? "ダーク" : m == LIGHT ? "ライト" : "端末に合わせる";
    }

    /** ダーク → ライト → 端末に合わせる → … と回す */
    public static void next(Context c) {
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putInt(KEY, (mode(c) + 1) % 3).apply();
    }

    /** いま暗い画面か（端末に合わせるときは端末の設定を見る） */
    public static boolean isDark(Context c) {
        int m = mode(c);
        if (m == DARK) return true;
        if (m == LIGHT) return false;
        int night = c.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return night == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    // 文字色は端末のテーマ任せにせず、はっきり決める（機種によって既定の文字色が薄い灰色になるため）
    /** 名前・見出し */
    public static int text(Context c) {
        return isDark(c) ? 0xFFF2F2F2 : 0xFF141414;
    }

    /** 最後の一言など、補足の文字 */
    public static int sub(Context c) {
        return isDark(c) ? 0xFFC4C4C4 : 0xFF4A4A4A;
    }

    /** 時刻・空のときの案内など、いちばん控えめな文字 */
    public static int muted(Context c) {
        return isDark(c) ? 0xFF9A9A9A : 0xFF707070;
    }

    /** super.onCreate より前に呼ぶこと */
    public static void apply(Activity a) {
        int m = mode(a);
        if (m == DARK) a.setTheme(android.R.style.Theme_DeviceDefault);
        else if (m == LIGHT) a.setTheme(android.R.style.Theme_DeviceDefault_Light);
        // AUTO はマニフェストの DayNight のまま
    }
}
