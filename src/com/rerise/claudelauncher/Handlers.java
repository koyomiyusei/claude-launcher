package com.rerise.claudelauncher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;

import java.util.ArrayList;
import java.util.List;

/** claude.ai のURLを開けるアプリの調査。PWA（WebAPK）の判別もここ */
public class Handlers {

    private static final String WEBAPK_PREFIX = "org.chromium.webapk";

    /** claude.ai を開けるアプリ一覧（自分自身は除く） */
    public static List<ResolveInfo> forClaude(Context c) {
        Intent probe = new Intent(Intent.ACTION_VIEW, Uri.parse("https://claude.ai/"));
        probe.addCategory(Intent.CATEGORY_BROWSABLE);
        List<ResolveInfo> out = new ArrayList<ResolveInfo>();
        List<String> seen = new ArrayList<String>();
        try {
            for (ResolveInfo ri : c.getPackageManager().queryIntentActivities(probe, PackageManager.MATCH_ALL)) {
                if (ri.activityInfo == null) continue;
                String p = ri.activityInfo.packageName;
                if (p == null || p.equals(c.getPackageName()) || seen.contains(p)) continue;
                seen.add(p);
                out.add(ri);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    /** インストール済みの claude.ai PWA（WebAPK）のパッケージ名。無ければ null */
    public static String pwaPackage(Context c) {
        for (ResolveInfo ri : forClaude(c)) {
            if (isPwa(ri.activityInfo.packageName)) return ri.activityInfo.packageName;
        }
        return null;
    }

    public static boolean isPwa(String pkg) {
        return pkg != null && pkg.startsWith(WEBAPK_PREFIX);
    }
}
