package com.rerise.claudelauncher;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.RemoteViews;

/** ホーム画面ウィジェット（登録した項目の一覧） */
public class LauncherWidget extends AppWidgetProvider {

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) m.updateAppWidget(id, build(c, id));
    }

    private RemoteViews build(Context c, int widgetId) {
        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget);

        // 一覧の中身は WidgetService が供給する
        Intent svc = new Intent(c, WidgetService.class);
        svc.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        svc.setData(android.net.Uri.parse(svc.toUri(Intent.URI_INTENT_SCHEME)));
        rv.setRemoteAdapter(R.id.widget_list, svc);
        rv.setEmptyView(R.id.widget_list, R.id.widget_empty);

        // 行をタップしたときの受け皿（URLは各行が差し込む）
        Intent open = new Intent(c, OpenActivity.class);
        open.setAction(Intent.ACTION_VIEW);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags |= PendingIntent.FLAG_MUTABLE;
        rv.setPendingIntentTemplate(R.id.widget_list,
                PendingIntent.getActivity(c, 0, open, flags));

        // タイトルをタップしたらアプリを開く
        int f2 = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) f2 |= PendingIntent.FLAG_IMMUTABLE;
        rv.setOnClickPendingIntent(R.id.widget_title,
                PendingIntent.getActivity(c, 1, new Intent(c, MainActivity.class), f2));

        return rv;
    }

    /** 項目を足したり並べ替えたりしたら呼ぶ。開いているウィジェットを描き直す */
    public static void refresh(Context c) {
        try {
            AppWidgetManager m = AppWidgetManager.getInstance(c);
            int[] ids = m.getAppWidgetIds(new ComponentName(c, LauncherWidget.class));
            if (ids == null || ids.length == 0) return;
            m.notifyAppWidgetViewDataChanged(ids, R.id.widget_list);
            Intent i = new Intent(c, LauncherWidget.class);
            i.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
            c.sendBroadcast(i);
        } catch (Exception ignored) {
        }
    }
}
