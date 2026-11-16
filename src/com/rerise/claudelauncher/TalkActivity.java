package com.rerise.claudelauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LINEのトーク一覧ふうの入口画面。
 * 上にタブ帯（横スクロール）、下にそのタブのトーク。行をタップすると該当セッションを開く。
 * 「最後の一言」と緑の丸は、右腕コネクタ（talk_add / talk_update）から届く（TalkSync）。手でも付け外しできる。
 */
public class TalkActivity extends Activity {

    private static final Pattern URL_RE = Pattern.compile("https?://[^\\s<>\"']+");
    private static final int ACCENT = 0xFFD97757;
    private static final int GREEN = 0xFF06C755;
    private static final String CLAUDE_APP = "com.anthropic.claude";


    private List<Talk> talks = new ArrayList<Talk>();
    private List<String> tabs = new ArrayList<String>();
    private final List<Talk> visible = new ArrayList<Talk>();
    private String current = "";

    private LinearLayout tabStrip;
    private TalkAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 端末によっては上のバー（とその中のメニュー）が出ないので、自前の見出しとメニューボタンを置く
        if (getActionBar() != null) getActionBar().hide();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setFitsSystemWindows(true);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(18), dp(10), dp(6), 0);
        TextView title = new TextView(this);
        title.setText("トーク");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final TextView more = new TextView(this);
        more.setText("メニュー ⋮");
        more.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        more.setPadding(dp(14), dp(10), dp(14), dp(10));
        more.setOnClickListener(v -> showMainMenu());
        header.addView(more);
        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        tabStrip = new LinearLayout(this);
        tabStrip.setOrientation(LinearLayout.HORIZONTAL);
        tabStrip.setPadding(dp(12), dp(10), dp(12), dp(10));
        hs.addView(tabStrip);
        root.addView(hs, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        View line = new View(this);
        line.setBackgroundColor(0x22808080);
        root.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));

        FrameLayout body = new FrameLayout(this);

        ListView list = new ListView(this);
        list.setDivider(new android.graphics.drawable.ColorDrawable(0x16808080));
        list.setDividerHeight(1);
        adapter = new TalkAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) -> open(visible.get(pos)));
        list.setOnItemLongClickListener((p, v, pos, id) -> {
            showRowMenu(visible.get(pos));
            return true;
        });
        body.addView(list, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView empty = new TextView(this);
        empty.setText("このタブにはまだトークがありません\n右下の ＋ から追加できます");
        empty.setGravity(Gravity.CENTER);
        empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        empty.setAlpha(0.5f);
        body.addView(empty, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        list.setEmptyView(empty);

        TextView fab = new TextView(this);
        fab.setText("＋");
        fab.setTextColor(Color.WHITE);
        fab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        fab.setGravity(Gravity.CENTER);
        GradientDrawable fbg = new GradientDrawable();
        fbg.setShape(GradientDrawable.OVAL);
        fbg.setColor(ACCENT);
        fab.setBackground(fbg);
        fab.setOnClickListener(v -> showEditor(null, null, null));
        FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(dp(56), dp(56),
                Gravity.BOTTOM | Gravity.END);
        fp.setMargins(0, 0, dp(20), dp(24));
        body.addView(fab, fp);

        root.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);

        reload();
        handleIncoming(getIntent());
        if (getIntent() != null && getIntent().getBooleanExtra("update", false)) Updater.check(this, false);
        Updater.autoCheck(this);
        NoticeJob.schedule(this);
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
        sync(false);
        Push.ensure(this);
    }

    /** サーバーから取ってきて混ぜる。loud=true のときだけ結果を知らせる */
    private void sync(final boolean loud) {
        if (!TalkStore.hasServer(this)) {
            if (loud) toast("先に「サーバー連携」を設定してください");
            return;
        }
        new Thread(() -> {
            final org.json.JSONArray items = TalkSync.fetch(getApplicationContext());
            runOnUiThread(() -> {
                if (isFinishing()) return;
                if (items == null) {
                    if (loud) toast("同期できませんでした");
                    return;
                }
                int n = TalkSync.merge(this, items).size();
                reload();
                if (loud) toast(n > 0 ? n + "件に返事が来ています" : "同期しました");
            });
        }).start();
    }

    private void showServerSetup() {
        final EditText in = new EditText(this);
        in.setSingleLine(true);
        in.setHint("https://… 合言葉");
        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(dp(20), dp(8), dp(20), 0);
        wrap.addView(in);
        boolean on = TalkStore.hasServer(this);
        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle("サーバー連携")
                .setMessage((on ? "いまは連携済みです（" + TalkStore.serverUrl(this) + "）。\n\n" : "")
                        + "右腕メモの ⚙ →「メモの置き場」に貼ってあるのと同じ1行（住所と合言葉）を貼ってください。"
                        + "Claudeに「トークに入れといて」と頼んだセッションが、ここに届くようになります。")
                .setView(wrap)
                .setNegativeButton("やめる", null)
                .setPositiveButton("保存", (d, w) -> {
                    String[] parts = in.getText().toString().trim().split("\\s+");
                    if (parts.length < 2 || !parts[0].startsWith("https://")) {
                        toast("「住所 合言葉」の形で貼ってください");
                        return;
                    }
                    String base = parts[0];
                    while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
                    TalkStore.setServer(this, base, parts[1]);
                    Push.forget(this);
                    sync(true);
                    Push.ensure(this);
                });
        if (on) {
            b.setNeutralButton("連携を外す", (d, w) -> {
                TalkStore.setServer(this, "", "");
                Push.forget(this);
                toast("連携を外しました");
            });
        }
        b.show();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncoming(intent);
        if (intent != null && intent.getBooleanExtra("update", false)) Updater.check(this, false);
    }

    private void showMainMenu() {
        String[] items = {"今すぐ同期", "サーバー連携", "アイコン一覧（ホーム画面用）", "更新確認",
                "プッシュ通知: " + Push.state(this),
                "並び順: " + (TalkStore.manualOrder(this) ? "自分で並べる" : "新しい順") + "（タップで切替）",
                "自動更新: " + Updater.autoState(this)};
        new AlertDialog.Builder(this)
                .setTitle("メニュー  v" + Updater.currentName(this))
                .setItems(items, (d, which) -> {
                    if (which == 0) sync(true);
                    else if (which == 1) showServerSetup();
                    else if (which == 2) startActivity(new Intent(this, MainActivity.class));
                    else if (which == 3) Updater.check(this, false);
                    else if (which == 4) {
                        Push.ensure(this);
                        toast("プッシュ通知の準備をやり直しました");
                    } else if (which == 5) toggleOrder();
                    else {
                        toast("新しい版を確認します");
                        new Thread(() -> Updater.silentUpdate(getApplicationContext(), true)).start();
                    }
                })
                .show();
    }

    // ---------------- 読み込みと並べ替え ----------------

    private void reload() {
        talks = TalkStore.load(this);
        tabs = TalkStore.loadTabs(this);
        current = TalkStore.currentTab(this);
        if (!tabs.contains(current)) current = tabs.get(0);
        // タブが消えて迷子になったトークは先頭のタブへ寄せる
        boolean moved = false;
        for (Talk t : talks) {
            if (!tabs.contains(t.tab)) {
                t.tab = tabs.get(0);
                moved = true;
            }
        }
        if (moved) TalkStore.save(this, talks);
        render();
    }

    private void render() {
        renderTabs();
        visible.clear();
        for (Talk t : talks) {
            if (current.equals(t.tab)) visible.add(t);
        }
        // ピン留めが上。その中は「新しい順」か「自分で並べた順」
        final boolean manual = TalkStore.manualOrder(this);
        Collections.sort(visible, new Comparator<Talk>() {
            public int compare(Talk a, Talk b) {
                if (a.pinned != b.pinned) return a.pinned ? -1 : 1;
                if (manual && a.order != b.order) return Long.compare(a.order, b.order);
                return Long.compare(b.time, a.time);
            }
        });
        adapter.notifyDataSetChanged();
    }

    /** 並び順を切り替える。自分で並べる側へ移るときは、いま見えている順をそのまま出発点にする */
    private void toggleOrder() {
        boolean manual = !TalkStore.manualOrder(this);
        if (manual) {
            List<Talk> sorted = new ArrayList<Talk>(talks);
            Collections.sort(sorted, new Comparator<Talk>() {
                public int compare(Talk a, Talk b) {
                    return Long.compare(b.time, a.time);
                }
            });
            long n = 1;
            for (Talk t : sorted) t.order = n++;
        }
        TalkStore.setManualOrder(this, manual);
        persist();
        toast(manual ? "自分で並べる：行を長押し →「上へ」「下へ」" : "新しい順に戻しました");
    }

    /** 見えている一覧の中で1つ動かす（ピン留めの組をまたがない）。dir=-1 上 / +1 下 / 0 一番上 */
    private void move(Talk t, int dir) {
        int i = visible.indexOf(t);
        if (i < 0) return;
        if (dir == 0) {
            long min = t.order;
            for (Talk x : visible) {
                if (x.pinned == t.pinned) min = Math.min(min, x.order);
            }
            t.order = min - 1;
        } else {
            int j = i + dir;
            if (j < 0 || j >= visible.size() || visible.get(j).pinned != t.pinned) return;
            Talk o = visible.get(j);
            long tmp = t.order;
            t.order = o.order;
            o.order = tmp;
            if (t.order == o.order) t.order += dir; // 同じ値どうしだったときの保険
        }
        persist();
    }

    private void persist() {
        TalkStore.save(this, talks);
        render();
    }

    // ---------------- タブ帯 ----------------

    private void renderTabs() {
        tabStrip.removeAllViews();
        for (final String tab : tabs) {
            int unread = 0;
            for (Talk t : talks) {
                if (t.unread && tab.equals(t.tab)) unread++;
            }
            boolean sel = tab.equals(current);
            TextView pill = pill(unread > 0 ? tab + "  " + unread : tab, sel);
            pill.setOnClickListener(v -> {
                current = tab;
                TalkStore.setCurrentTab(this, tab);
                render();
            });
            pill.setOnLongClickListener(v -> {
                showTabMenu(tab);
                return true;
            });
            tabStrip.addView(pill, pillParams());
        }
        TextView plus = pill("＋", false);
        plus.setOnClickListener(v -> askTabName(null));
        tabStrip.addView(plus, pillParams());
    }

    private LinearLayout.LayoutParams pillParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(8);
        return lp;
    }

    private TextView pill(String text, boolean selected) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setSingleLine(true);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        t.setPadding(dp(16), dp(8), dp(16), dp(8));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(20));
        if (selected) {
            bg.setColor(ACCENT);
            t.setTextColor(Color.WHITE);
            t.setTypeface(Typeface.DEFAULT_BOLD);
        } else {
            bg.setColor(0x14808080);
        }
        t.setBackground(bg);
        return t;
    }

    private void showTabMenu(final String tab) {
        new AlertDialog.Builder(this)
                .setTitle(tab)
                .setItems(new String[]{"名前を変える", "左へ動かす", "右へ動かす", "タブを削除"}, (d, which) -> {
                    int i = tabs.indexOf(tab);
                    if (which == 0) {
                        askTabName(tab);
                    } else if (which == 1 || which == 2) {
                        int j = which == 1 ? i - 1 : i + 1;
                        if (j < 0 || j >= tabs.size()) return;
                        Collections.swap(tabs, i, j);
                        TalkStore.saveTabs(this, tabs);
                        render();
                    } else {
                        deleteTab(tab);
                    }
                })
                .show();
    }

    /** old が null なら新規作成、あれば名前変更 */
    private void askTabName(final String old) {
        final EditText in = new EditText(this);
        in.setSingleLine(true);
        in.setHint("例：YouTube");
        if (old != null) in.setText(old);
        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(dp(20), dp(8), dp(20), 0);
        wrap.addView(in);
        new AlertDialog.Builder(this)
                .setTitle(old == null ? "タブを追加" : "タブの名前を変える")
                .setView(wrap)
                .setNegativeButton("やめる", null)
                .setPositiveButton("決定", (d, w) -> {
                    String name = in.getText().toString().trim();
                    if (name.isEmpty()) return;
                    if (tabs.contains(name)) {
                        if (!name.equals(old)) toast("同じ名前のタブがあります");
                        return;
                    }
                    if (old == null) {
                        tabs.add(name);
                    } else {
                        tabs.set(tabs.indexOf(old), name);
                        for (Talk t : talks) {
                            if (old.equals(t.tab)) t.tab = name;
                        }
                        TalkStore.save(this, talks);
                    }
                    current = name;
                    TalkStore.saveTabs(this, tabs);
                    TalkStore.setCurrentTab(this, name);
                    render();
                })
                .show();
    }

    private void deleteTab(final String tab) {
        if (tabs.size() <= 1) {
            toast("最後のタブは消せません");
            return;
        }
        int n = 0;
        for (Talk t : talks) {
            if (tab.equals(t.tab)) n++;
        }
        final String dest = tabs.get(tabs.indexOf(tab) == 0 ? 1 : 0);
        new AlertDialog.Builder(this)
                .setTitle("「" + tab + "」を削除")
                .setMessage(n == 0 ? "このタブを削除します。"
                        : "中の" + n + "件のトークは「" + dest + "」へ移します。")
                .setNegativeButton("やめる", null)
                .setPositiveButton("削除", (d, w) -> {
                    for (Talk t : talks) {
                        if (tab.equals(t.tab)) t.tab = dest;
                    }
                    tabs.remove(tab);
                    if (tab.equals(current)) current = dest;
                    TalkStore.saveTabs(this, tabs);
                    TalkStore.setCurrentTab(this, current);
                    persist();
                })
                .show();
    }

    // ---------------- 行の操作 ----------------

    private void open(Talk t) {
        if (t.url == null || t.url.trim().isEmpty()) {
            toast("URLが未設定です。長押し → 編集で入れてください");
            return;
        }
        if (t.unread) {
            t.unread = false;
            persist();
        }
        Intent i = new Intent(this, OpenActivity.class);
        i.putExtra("url", t.url);
        i.putExtra("pkg", t.pkg);
        startActivity(i);
    }

    private void showRowMenu(final Talk t) {
        final boolean manual = TalkStore.manualOrder(this);
        String[] base = {
                t.pinned ? "ピン留めを外す" : "ピン留めする",
                t.unread ? "緑の丸を消す" : "緑の丸を付ける",
                "編集",
                "削除"
        };
        String[] items = base;
        if (manual) {
            items = new String[]{base[0], base[1], base[2], base[3], "↑ 上へ", "↓ 下へ", "⤒ 一番上へ"};
        }
        new AlertDialog.Builder(this)
                .setTitle(t.name)
                .setItems(items, (d, which) -> {
                    if (which == 0) {
                        t.pinned = !t.pinned;
                        persist();
                    } else if (which == 1) {
                        t.unread = !t.unread;
                        if (t.unread) t.time = System.currentTimeMillis();
                        persist();
                    } else if (which == 2) {
                        showEditor(t, null, null);
                    } else if (which == 4) {
                        move(t, -1);
                    } else if (which == 5) {
                        move(t, 1);
                    } else if (which == 6) {
                        move(t, 0);
                    } else {
                        new AlertDialog.Builder(this)
                                .setMessage("「" + t.name + "」を一覧から削除しますか？\n（Claude側のセッションは消えません）")
                                .setNegativeButton("やめる", null)
                                .setPositiveButton("削除", (d2, w2) -> {
                                    if (t.sid != null && !t.sid.isEmpty()) {
                                        TalkStore.addDismissed(this, t.sid);
                                        TalkSync.removeRemote(this, t.sid);
                                    }
                                    talks.remove(t);
                                    persist();
                                })
                                .show();
                    }
                })
                .show();
    }

    // ---------------- 追加・編集 ----------------

    private void showEditor(final Talk editing, String presetName, String presetUrl) {
        final boolean isNew = editing == null;
        final Talk t = isNew ? new Talk() : editing;
        final String[] tabChoice = {isNew ? current : t.tab};
        final String[] pkgChoice = {isNew ? defaultPkg() : (t.pkg == null ? "" : t.pkg)};

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(4), dp(20), dp(4));

        form.addView(label("セッション名"));
        final EditText name = new EditText(this);
        name.setSingleLine(true);
        name.setText(isNew ? (presetName == null ? "" : presetName) : t.name);
        form.addView(name);

        form.addView(label("URL（Claudeのセッションのリンク）"));
        final EditText url = new EditText(this);
        url.setSingleLine(true);
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        String u = isNew ? presetUrl : t.url;
        if (isNew && u == null) {
            // コピー済みの claude.ai のリンクがあれば入れておく
            String clip = extractUrl(clipboard());
            if (clip != null && clip.contains("claude.ai") && !exists(clip)) u = clip;
        }
        url.setText(u == null ? "" : u);
        form.addView(url);

        form.addView(label("最後の一言（空でもよい）"));
        final EditText last = new EditText(this);
        last.setSingleLine(true);
        last.setText(isNew ? "" : t.last);
        form.addView(last);

        form.addView(label("タブ"));
        final TextView tabBtn = chooser(tabChoice[0]);
        tabBtn.setOnClickListener(v -> {
            final String[] names = tabs.toArray(new String[0]);
            new AlertDialog.Builder(this)
                    .setItems(names, (d, which) -> {
                        tabChoice[0] = names[which];
                        tabBtn.setText(names[which]);
                    })
                    .show();
        });
        form.addView(tabBtn);

        form.addView(label("開き先"));
        final List<ResolveInfo> apps = Handlers.forClaude(this);
        final TextView pkgBtn = chooser(pkgLabel(pkgChoice[0], apps));
        pkgBtn.setOnClickListener(v -> {
            final String[] labels = new String[apps.size() + 1];
            labels[0] = "おまかせ";
            for (int i = 0; i < apps.size(); i++) labels[i + 1] = appLabel(apps.get(i));
            new AlertDialog.Builder(this)
                    .setItems(labels, (d, which) -> {
                        pkgChoice[0] = which == 0 ? "" : apps.get(which - 1).activityInfo.packageName;
                        pkgBtn.setText(labels[which]);
                    })
                    .show();
        });
        form.addView(pkgBtn);

        ScrollView sv = new ScrollView(this);
        sv.addView(form);

        new AlertDialog.Builder(this)
                .setTitle(isNew ? "トークを追加" : "トークを編集")
                .setView(sv)
                .setNegativeButton("やめる", null)
                .setPositiveButton("保存", (d, w) -> {
                    String n = name.getText().toString().trim();
                    String link = url.getText().toString().trim();
                    if (n.isEmpty()) n = link.isEmpty() ? "名前なし" : link;
                    t.name = n;
                    t.url = link;
                    t.last = last.getText().toString().trim();
                    t.tab = tabChoice[0];
                    t.pkg = pkgChoice[0];
                    if (isNew) {
                        t.color = Store.PALETTE[talks.size() % Store.PALETTE.length];
                        long min = 0;
                        for (Talk x : talks) min = Math.min(min, x.order);
                        t.order = min - 1;
                        t.time = System.currentTimeMillis();
                        talks.add(t);
                        current = t.tab;
                        TalkStore.setCurrentTab(this, current);
                    }
                    persist();
                })
                .show();
    }

    private boolean exists(String url) {
        for (Talk t : talks) {
            if (url.equals(t.url)) return true;
        }
        return false;
    }

    /** Claudeアプリが入っていればそれを既定の開き先にする */
    private String defaultPkg() {
        for (ResolveInfo ri : Handlers.forClaude(this)) {
            if (CLAUDE_APP.equals(ri.activityInfo.packageName)) return CLAUDE_APP;
        }
        return "";
    }

    private String pkgLabel(String pkg, List<ResolveInfo> apps) {
        if (pkg == null || pkg.isEmpty()) return "おまかせ";
        for (ResolveInfo ri : apps) {
            if (pkg.equals(ri.activityInfo.packageName)) return appLabel(ri);
        }
        return pkg;
    }

    private String appLabel(ResolveInfo ri) {
        CharSequence cs = ri.loadLabel(getPackageManager());
        String s = cs == null ? ri.activityInfo.packageName : cs.toString();
        return Handlers.isPwa(ri.activityInfo.packageName) ? s + "（PWA）" : s;
    }

    // ---------------- 共有メニューからの追加 ----------------

    private void handleIncoming(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) return;
        String text = intent.getStringExtra(Intent.EXTRA_TEXT);
        String subject = intent.getStringExtra(Intent.EXTRA_SUBJECT);
        String url = extractUrl(text);
        // 二重に開かないよう、処理済みのインテントは印を付けておく
        intent.setAction(null);
        if (url == null) {
            toast("URLが見つかりませんでした");
            return;
        }
        showEditor(null, cleanTitle(subject), url);
    }

    private String extractUrl(String text) {
        if (text == null) return null;
        Matcher m = URL_RE.matcher(text);
        return m.find() ? m.group() : null;
    }

    private String cleanTitle(String title) {
        if (title == null) return null;
        String t = title.trim();
        String[] tails = {" - Claude", " | Claude", " — Claude", " - claude.ai"};
        for (String tail : tails) {
            if (t.endsWith(tail)) t = t.substring(0, t.length() - tail.length()).trim();
        }
        if (t.isEmpty() || t.equalsIgnoreCase("Claude")) return null;
        return t;
    }

    private String clipboard() {
        try {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null || !cm.hasPrimaryClip()) return null;
            ClipData clip = cm.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) return null;
            CharSequence cs = clip.getItemAt(0).coerceToText(this);
            return cs == null ? null : cs.toString();
        } catch (Exception e) {
            return null;
        }
    }

    // ---------------- 部品 ----------------

    private TextView label(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        t.setAlpha(0.6f);
        t.setPadding(0, dp(14), 0, dp(2));
        return t;
    }

    private TextView chooser(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        t.setPadding(dp(14), dp(10), dp(14), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(10));
        bg.setColor(0x14808080);
        t.setBackground(bg);
        return t;
    }

    private String timeLabel(long ms) {
        Calendar now = Calendar.getInstance();
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ms);
        if (sameDay(now, c)) {
            return String.format(java.util.Locale.US, "%d:%02d",
                    c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
        }
        now.add(Calendar.DAY_OF_YEAR, -1);
        if (sameDay(now, c)) return "昨日";
        String md = (c.get(Calendar.MONTH) + 1) + "/" + c.get(Calendar.DAY_OF_MONTH);
        if (Calendar.getInstance().get(Calendar.YEAR) == c.get(Calendar.YEAR)) return md;
        return c.get(Calendar.YEAR) + "/" + md;
    }

    private boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private class TalkAdapter extends BaseAdapter {
        public int getCount() {
            return visible.size();
        }

        public Object getItem(int i) {
            return visible.get(i);
        }

        public long getItemId(int i) {
            return i;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            Talk t = visible.get(position);
            Context c = TalkActivity.this;

            LinearLayout row = new LinearLayout(c);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(16), dp(12), dp(16), dp(12));
            if (t.pinned) row.setBackgroundColor(0x0C808080);

            ImageView iv = new ImageView(c);
            iv.setImageBitmap(IconGen.circle(t.name, t.color, dp(48)));
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(48), dp(48));
            ip.rightMargin = dp(14);
            row.addView(iv, ip);

            LinearLayout col = new LinearLayout(c);
            col.setOrientation(LinearLayout.VERTICAL);

            TextView name = new TextView(c);
            name.setText(t.pinned ? "📌 " + t.name : t.name);
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            name.setTypeface(Typeface.DEFAULT_BOLD);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            col.addView(name);

            TextView last = new TextView(c);
            last.setText(t.last == null || t.last.isEmpty() ? " " : t.last);
            last.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            last.setAlpha(0.6f);
            last.setSingleLine(true);
            last.setEllipsize(TextUtils.TruncateAt.END);
            last.setPadding(0, dp(2), 0, 0);
            col.addView(last);

            row.addView(col, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            LinearLayout right = new LinearLayout(c);
            right.setOrientation(LinearLayout.VERTICAL);
            right.setGravity(Gravity.END);
            right.setPadding(dp(10), 0, 0, 0);

            TextView time = new TextView(c);
            time.setText(timeLabel(t.time));
            time.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            time.setAlpha(0.5f);
            time.setSingleLine(true);
            // 幅を「中身ぶん」と明示する。既定（親いっぱい）のままだと、右の列の幅が緑の丸の幅で決まってしまい、時刻が切れる
            right.addView(time, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            View dot = new View(c);
            dot.setBackground(IconGen.dot(GREEN));
            dot.setVisibility(t.unread ? View.VISIBLE : View.INVISIBLE);
            LinearLayout.LayoutParams dp2 = new LinearLayout.LayoutParams(dp(14), dp(14));
            dp2.topMargin = dp(6);
            right.addView(dot, dp2);

            row.addView(right, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return row;
        }
    }
}
