package com.rerise.claudelauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    private static final Pattern URL_RE = Pattern.compile("https?://[^\\s<>\"']+");

    private List<Entry> entries = new ArrayList<Entry>();
    private Adapter adapter;
    private boolean dialogOpen = false;
    private String lastOfferedUrl = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        entries = Store.load(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(16));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("Claudeランチャー");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button update = new Button(this);
        update.setText("更新確認");
        update.setAllCaps(false);
        update.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        update.setOnClickListener(v -> Updater.check(MainActivity.this, false));
        header.addView(update);

        root.addView(header);

        TextView sub = new TextView(this);
        sub.setText("よく使うチャットやプロジェクトをホーム画面に置く　v" + Updater.currentName(this));
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        sub.setAlpha(0.6f);
        sub.setPadding(0, dp(4), 0, dp(16));
        root.addView(sub);

        ListView list = new ListView(this);
        list.setDivider(null);
        list.setDividerHeight(dp(8));
        adapter = new Adapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> showActions(entries.get(position)));
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            showEditor(entries.get(position));
            return true;
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(list, lp);

        Button add = new Button(this);
        add.setText("＋  追加");
        add.setAllCaps(false);
        add.setOnClickListener(v -> showEditor(null));
        root.addView(add, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView hint = new TextView(this);
        hint.setText("項目をタップ → 操作メニュー / 長押し → 編集");
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        hint.setAlpha(0.45f);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(10), 0, 0);
        root.addView(hint);

        setContentView(root);
        handleIncoming(getIntent());
        Updater.autoCheck(this);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        entries = Store.load(this);
        if (adapter != null) adapter.notifyDataSetChanged();
        handleIncoming(intent);
    }

    // ---------------- 共有メニューからの追加 ----------------

    /** 他アプリから「共有」されてきたURLを拾って追加画面を開く */
    private void handleIncoming(Intent intent) {
        if (intent == null) return;
        if (!Intent.ACTION_SEND.equals(intent.getAction())) return;

        String text = intent.getStringExtra(Intent.EXTRA_TEXT);
        String subject = intent.getStringExtra(Intent.EXTRA_SUBJECT);
        String url = extractUrl(text);
        if (url == null) {
            toast("URLが見つかりませんでした");
            return;
        }
        // 二重に開かないよう、処理済みのインテントは印を付けておく
        intent.setAction(null);
        lastOfferedUrl = url;
        showEditor(null, cleanTitle(subject != null ? subject : firstLine(text)), url);
    }

    private String extractUrl(String text) {
        if (text == null) return null;
        Matcher m = URL_RE.matcher(text);
        return m.find() ? m.group() : null;
    }

    private String firstLine(String text) {
        if (text == null) return null;
        String[] lines = text.split("\\r?\\n");
        for (String l : lines) {
            String t = l.trim();
            if (!t.isEmpty() && !t.startsWith("http")) return t;
        }
        return null;
    }

    /** ブラウザのページタイトルから末尾の「 - Claude」などを落とす */
    private String cleanTitle(String title) {
        if (title == null) return null;
        String t = title.trim();
        String[] tails = {" - Claude", " | Claude", " — Claude", " - claude.ai"};
        for (String tail : tails) {
            if (t.endsWith(tail)) t = t.substring(0, t.length() - tail.length()).trim();
        }
        if (t.equalsIgnoreCase("Claude")) return null;
        return t.isEmpty() ? null : t;
    }

    // ---------------- クリップボード検知 ----------------

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) maybeOfferClipboard();
    }

    private void maybeOfferClipboard() {
        if (dialogOpen) return;
        String url = extractUrl(clipboard());
        if (url == null || !url.contains("claude.ai")) return;
        if (url.equals(lastOfferedUrl)) return;
        lastOfferedUrl = url;
        for (Entry e : entries) {
            if (url.equals(e.url)) return;
        }
        dialogOpen = true;
        new AlertDialog.Builder(this)
                .setTitle("ClaudeのURLがコピーされています")
                .setMessage(url + "\n\nこれを一覧に追加しますか？")
                .setNegativeButton("いいえ", (d, w) -> dialogOpen = false)
                .setOnCancelListener(d -> dialogOpen = false)
                .setPositiveButton("追加", (d, w) -> {
                    dialogOpen = false;
                    showEditor(null, null, url);
                })
                .show();
    }

    // ---------------- 操作メニュー ----------------

    private void showActions(final Entry e) {
        String[] items = {"ホーム画面に追加", "開いてみる", "編集", "削除"};
        new AlertDialog.Builder(this)
                .setTitle(e.name)
                .setItems(items, (dialog, which) -> {
                    if (which == 0) pin(e);
                    else if (which == 1) open(e);
                    else if (which == 2) showEditor(e);
                    else confirmDelete(e);
                })
                .show();
    }

    private void open(Entry e) {
        Intent i = new Intent(this, OpenActivity.class);
        i.setAction(Intent.ACTION_VIEW);
        i.putExtra("url", e.url);
        i.putExtra("browser", e.browser);
        i.putExtra("pkg", e.pkg);
        startActivity(i);
    }

    private void pin(Entry e) {
        ShortcutManager sm = getSystemService(ShortcutManager.class);
        if (sm == null || !sm.isRequestPinShortcutSupported()) {
            toast("このホームアプリはアイコンの自動追加に対応していません");
            return;
        }
        sm.requestPinShortcut(buildShortcut(e), null);
    }

    private ShortcutInfo buildShortcut(Entry e) {
        Intent i = new Intent(this, OpenActivity.class);
        i.setAction(Intent.ACTION_VIEW);
        i.putExtra("url", e.url);
        i.putExtra("browser", e.browser);
        i.putExtra("pkg", e.pkg);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        return new ShortcutInfo.Builder(this, "cl_" + e.id)
                .setShortLabel(e.name)
                .setLongLabel(e.name)
                .setIcon(Icon.createWithAdaptiveBitmap(IconGen.adaptive(e.icon, e.color)))
                .setIntent(i)
                .build();
    }

    private void confirmDelete(final Entry e) {
        new AlertDialog.Builder(this)
                .setTitle("削除")
                .setMessage("「" + e.name + "」を一覧から削除します。ホーム画面のアイコンは無効になります。")
                .setNegativeButton("やめる", null)
                .setPositiveButton("削除", (d, w) -> {
                    ShortcutManager sm = getSystemService(ShortcutManager.class);
                    if (sm != null) {
                        try {
                            sm.disableShortcuts(Arrays.asList("cl_" + e.id), "この項目は削除されました");
                        } catch (Exception ignored) {
                        }
                    }
                    entries.remove(e);
                    Store.save(this, entries);
                    adapter.notifyDataSetChanged();
                })
                .show();
    }

    // ---------------- 追加・編集 ----------------

    private void showEditor(final Entry existing) {
        showEditor(existing, null, null);
    }

    private void showEditor(final Entry existing, String presetName, String presetUrl) {
        final boolean isNew = (existing == null);
        final Entry e = isNew
                ? new Entry(Store.newId(),
                            presetName == null ? "" : presetName,
                            presetUrl == null ? "https://claude.ai/" : presetUrl,
                            "", Store.PALETTE[entries.size() % Store.PALETTE.length], false)
                : existing;
        dialogOpen = true;

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), dp(0));

        box.addView(label("名前（アイコンの下に出る文字）"));
        final EditText name = new EditText(this);
        name.setSingleLine(true);
        name.setHint("給与計算");
        name.setText(e.name);
        box.addView(name);

        box.addView(label("URL"));
        final EditText url = new EditText(this);
        url.setSingleLine(true);
        url.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        url.setHint("https://claude.ai/chat/xxxxxxxx");
        url.setText(e.url);
        box.addView(url);

        Button paste = new Button(this);
        paste.setText("クリップボードから貼り付け");
        paste.setAllCaps(false);
        paste.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        paste.setOnClickListener(v -> {
            String t = clipboard();
            if (t == null) toast("クリップボードにテキストがありません");
            else url.setText(t.trim());
        });
        box.addView(paste);

        box.addView(label("アイコンの文字（1〜2文字・絵文字も可）"));
        final EditText icon = new EditText(this);
        icon.setSingleLine(true);
        icon.setHint("給");
        icon.setText(e.icon);
        box.addView(icon);

        box.addView(label("色"));
        final int[] chosen = {e.color};
        final List<View> swatches = new ArrayList<View>();
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int c : Store.PALETTE) {
            final int col = c;
            View sw = new View(this);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(34), dp(34));
            p.rightMargin = dp(8);
            sw.setLayoutParams(p);
            sw.setBackground(swatchBg(col, col == chosen[0]));
            sw.setOnClickListener(v -> {
                chosen[0] = col;
                for (View other : swatches) {
                    int oc = (Integer) other.getTag();
                    other.setBackground(swatchBg(oc, oc == col));
                }
            });
            sw.setTag(col);
            swatches.add(sw);
            row.addView(sw);
        }
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(row);
        box.addView(hs);

        box.addView(label("開き方"));
        final String[] chosenPkg = {e.pkg == null ? "" : e.pkg};
        final Button openWith = new Button(this);
        openWith.setAllCaps(false);
        openWith.setText(handlerLabel(chosenPkg[0]));
        openWith.setOnClickListener(v -> pickHandler(chosenPkg, openWith));
        box.addView(openWith);

        TextView openHint = new TextView(this);
        openHint.setText("claude.ai をPWAとしてインストールしていれば、ここで選べます");
        openHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        openHint.setAlpha(0.45f);
        box.addView(openHint);

        new AlertDialog.Builder(this)
                .setTitle(isNew ? "追加" : "編集")
                .setView(wrapScroll(box))
                .setNegativeButton("やめる", (d, w) -> dialogOpen = false)
                .setOnCancelListener(d -> dialogOpen = false)
                .setPositiveButton("保存", (d, w) -> {
                    dialogOpen = false;
                    String n = name.getText().toString().trim();
                    String u = url.getText().toString().trim();
                    if (n.isEmpty()) n = "無題";
                    if (u.isEmpty()) {
                        toast("URLが空です");
                        return;
                    }
                    if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://" + u;
                    e.name = n;
                    e.url = u;
                    e.icon = icon.getText().toString().trim();
                    if (e.icon.isEmpty()) e.icon = n.substring(0, 1);
                    e.color = chosen[0];
                    e.pkg = chosenPkg[0];
                    if (isNew) entries.add(e);
                    Store.save(this, entries);
                    adapter.notifyDataSetChanged();

                    ShortcutManager sm = getSystemService(ShortcutManager.class);
                    if (sm != null && !isNew) {
                        try {
                            sm.updateShortcuts(Arrays.asList(buildShortcut(e)));
                        } catch (Exception ignored) {
                        }
                    }
                    if (isNew) askPin(e);
                })
                .show();
    }

    private void askPin(final Entry e) {
        new AlertDialog.Builder(this)
                .setTitle("ホーム画面に追加しますか？")
                .setMessage("「" + e.name + "」のアイコンをホーム画面に置きます。")
                .setNegativeButton("あとで", null)
                .setPositiveButton("追加", (d, w) -> pin(e))
                .show();
    }

    // ---------------- 開き先アプリの選択 ----------------

    /** claude.ai のURLを開けるアプリを列挙する（ブラウザ・Claudeアプリ・PWAのWebAPKなど） */
    private List<ResolveInfo> handlers() {
        Intent probe = new Intent(Intent.ACTION_VIEW, Uri.parse("https://claude.ai/"));
        probe.addCategory(Intent.CATEGORY_BROWSABLE);
        List<ResolveInfo> all = getPackageManager().queryIntentActivities(probe, PackageManager.MATCH_ALL);
        List<ResolveInfo> out = new ArrayList<ResolveInfo>();
        List<String> seen = new ArrayList<String>();
        for (ResolveInfo ri : all) {
            if (ri.activityInfo == null) continue;
            String p = ri.activityInfo.packageName;
            if (p == null || p.equals(getPackageName()) || seen.contains(p)) continue;
            seen.add(p);
            out.add(ri);
        }
        return out;
    }

    private String handlerLabel(String pkg) {
        if (pkg == null || pkg.isEmpty()) return "自動（Androidに任せる）";
        for (ResolveInfo ri : handlers()) {
            if (pkg.equals(ri.activityInfo.packageName)) return prettyName(ri);
        }
        return pkg + "（見つかりません）";
    }

    private String prettyName(ResolveInfo ri) {
        String name;
        try {
            name = ri.loadLabel(getPackageManager()).toString();
        } catch (Exception e) {
            name = ri.activityInfo.packageName;
        }
        if (ri.activityInfo.packageName.startsWith("org.chromium.webapk")) name += "（PWA）";
        return name;
    }

    private void pickHandler(final String[] chosenPkg, final Button button) {
        final List<ResolveInfo> list = handlers();
        final String[] labels = new String[list.size() + 1];
        final String[] pkgs = new String[list.size() + 1];
        labels[0] = "自動（Androidに任せる）";
        pkgs[0] = "";
        for (int i = 0; i < list.size(); i++) {
            labels[i + 1] = prettyName(list.get(i));
            pkgs[i + 1] = list.get(i).activityInfo.packageName;
        }
        new AlertDialog.Builder(this)
                .setTitle("どのアプリで開く？")
                .setItems(labels, (d, which) -> {
                    chosenPkg[0] = pkgs[which];
                    button.setText(labels[which]);
                })
                .show();
    }

    // ---------------- 部品 ----------------

    private View wrapScroll(View child) {
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        sv.addView(child);
        return sv;
    }

    private TextView label(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        t.setAlpha(0.6f);
        t.setPadding(0, dp(14), 0, dp(2));
        return t;
    }

    private GradientDrawable swatchBg(int color, boolean selected) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        if (selected) g.setStroke(dp(3), Color.parseColor("#888888"));
        return g;
    }

    private String clipboard() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null || !cm.hasPrimaryClip()) return null;
        ClipData clip = cm.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) return null;
        CharSequence cs = clip.getItemAt(0).coerceToText(this);
        return cs == null ? null : cs.toString();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private class Adapter extends BaseAdapter {
        public int getCount() {
            return entries.size();
        }

        public Object getItem(int i) {
            return entries.get(i);
        }

        public long getItemId(int i) {
            return i;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            Entry e = entries.get(position);

            LinearLayout rowView = new LinearLayout(MainActivity.this);
            rowView.setOrientation(LinearLayout.HORIZONTAL);
            rowView.setGravity(Gravity.CENTER_VERTICAL);
            rowView.setPadding(dp(12), dp(12), dp(12), dp(12));
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(dp(14));
            bg.setColor(0x14808080);
            rowView.setBackground(bg);

            ImageView iv = new ImageView(MainActivity.this);
            iv.setImageBitmap(IconGen.circle(e.icon, e.color, dp(44)));
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(44), dp(44));
            ip.rightMargin = dp(14);
            rowView.addView(iv, ip);

            LinearLayout col = new LinearLayout(MainActivity.this);
            col.setOrientation(LinearLayout.VERTICAL);

            TextView n = new TextView(MainActivity.this);
            n.setText(e.name);
            n.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            n.setTypeface(Typeface.DEFAULT_BOLD);
            col.addView(n);

            TextView u = new TextView(MainActivity.this);
            u.setText(shorten(e.url));
            u.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            u.setAlpha(0.5f);
            u.setSingleLine(true);
            col.addView(u);

            rowView.addView(col, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            return rowView;
        }

        private String shorten(String url) {
            try {
                Uri u = Uri.parse(url);
                String path = u.getPath();
                if (path == null) path = "";
                if (path.length() > 28) path = path.substring(0, 28) + "…";
                return u.getHost() + path;
            } catch (Exception e) {
                return url;
            }
        }
    }
}
