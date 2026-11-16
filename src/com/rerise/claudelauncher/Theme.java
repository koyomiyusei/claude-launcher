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

    /** super.onCreate より前に呼ぶこと */
    public static void apply(Activity a) {
        int m = mode(a);
        if (m == DARK) a.setTheme(android.R.style.Theme_DeviceDefault);
        else if (m == LIGHT) a.setTheme(android.R.style.Theme_DeviceDefault_Light);
        // AUTO はマニフェストの DayNight のまま
    }
}
