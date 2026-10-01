package com.rerise.claudelauncher;

import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import java.util.ArrayList;
import java.util.List;

/** ウィジェットの一覧に行を供給する */
public class WidgetService extends RemoteViewsService {

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new Factory(getApplicationContext());
    }

    private static class Factory implements RemoteViewsService.RemoteViewsFactory {

        private final Context ctx;
        private List<Entry> items = new ArrayList<Entry>();

        Factory(Context c) {
            ctx = c;
        }

        public void onCreate() {
            reload();
        }

        public void onDataSetChanged() {
            reload();
        }

        private void reload() {
            items = Store.load(ctx);
        }

        public void onDestroy() {
            items.clear();
        }

        public int getCount() {
            return items.size();
        }

        public RemoteViews getViewAt(int position) {
            if (position < 0 || position >= items.size()) return null;
            Entry e = items.get(position);

            RemoteViews rv = new RemoteViews(ctx.getPackageName(), R.layout.widget_item);
            int px = Math.round(34 * ctx.getResources().getDisplayMetrics().density);
            rv.setImageViewBitmap(R.id.item_icon, IconGen.circle(e.icon, e.color, px));
            rv.setTextViewText(R.id.item_name, e.name);

            Intent fill = new Intent();
            fill.putExtra("url", e.url);
            fill.putExtra("pkg", e.pkg == null ? "" : e.pkg);
            fill.putExtra("browser", e.browser);
            rv.setOnClickFillInIntent(R.id.widget_row, fill);
            return rv;
        }

        public RemoteViews getLoadingView() {
            return null;
        }

        public int getViewTypeCount() {
            return 1;
        }

        public long getItemId(int position) {
            return position;
        }

        public boolean hasStableIds() {
            return false;
        }
    }
}
