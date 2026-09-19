package com.rerise.claudelauncher;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

public class OpenActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String url = getIntent() == null ? null : getIntent().getStringExtra("url");
        if (url == null || url.trim().isEmpty()) {
            Toast.makeText(this, "URLが設定されていません", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        boolean browser = getIntent().getBooleanExtra("browser", false);
        String pkg = getIntent().getStringExtra("pkg");
        Intent view = new Intent(Intent.ACTION_VIEW, Uri.parse(url.trim()));
        view.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        view.addCategory(Intent.CATEGORY_BROWSABLE);

        if (pkg != null && !pkg.trim().isEmpty()) {
            // 指定のアプリ（ブラウザ・ClaudeアプリまたはPWAのWebAPK）で開く
            view.setPackage(pkg.trim());
        } else if (browser) {
            String b = defaultBrowser();
            if (b != null) view.setPackage(b);
        }

        try {
            startActivity(view);
        } catch (Exception e) {
            try {
                view.setPackage(null);
                startActivity(view);
            } catch (Exception e2) {
                Toast.makeText(this, "開けませんでした: " + url, Toast.LENGTH_LONG).show();
            }
        }
        finish();
    }

    /** 既定ブラウザのパッケージ名を調べる（Claudeアプリに横取りさせたくないとき用） */
    private String defaultBrowser() {
        try {
            Intent probe = new Intent(Intent.ACTION_VIEW, Uri.parse("http://example.com"));
            probe.addCategory(Intent.CATEGORY_BROWSABLE);
            ResolveInfo ri = getPackageManager().resolveActivity(probe, 0);
            if (ri != null && ri.activityInfo != null) {
                String pkg = ri.activityInfo.packageName;
                if (!"android".equals(pkg) && !pkg.contains("resolver")) return pkg;
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
