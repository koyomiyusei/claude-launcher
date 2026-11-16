package com.rerise.claudelauncher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

/**
 * 自動更新の結果を受け取る。
 * - 入れ替えが済んだ（アプリが新しくなって起き直した）→「更新しました」
 * - 端末が確認を求めてきた → 通知を出して、タップしたら今までどおりの手順で入れる
 */
public class UpdateReceiver extends BroadcastReceiver {

    public static final String ACTION_RESULT = "com.rerise.claudelauncher.AUTO_UPDATE_RESULT";
    private static final int NOTE_ID = 7701;

    @Override
    public void onReceive(Context c, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            String v = Updater.currentName(c);
            Updater.setAutoState(c, "v" + v + " に更新済み");
            Notices.notifySimple(c, NOTE_ID, "Claudeランチャーを更新しました", "v" + v + " になりました",
                    new Intent(c, TalkActivity.class));
            // 更新のあとも定期の確認とプッシュ通知が続くように起こし直す
            NoticeJob.schedule(c);
            Push.ensure(c);
            return;
        }
        if (!ACTION_RESULT.equals(action)) return;
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        String ver = intent.getStringExtra("ver");
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Updater.setAutoState(c, "この端末は確認が必要でした（通知から更新してください）");
            askToUpdate(c, ver);
        } else if (status != PackageInstaller.STATUS_SUCCESS) {
            String m = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            Updater.setAutoState(c, "入れられませんでした: " + (m == null ? ("コード " + status) : m));
        }
    }

    /** 自動では入れられなかったとき。タップするとアプリが開いて、今までどおりの「更新確認」が走る */
    public static void askToUpdate(Context c, String ver) {
        Intent open = new Intent(c, TalkActivity.class);
        open.putExtra("update", true);
        Notices.notifySimple(c, NOTE_ID, "Claudeランチャーの新しい版があります",
                "v" + (ver == null ? "?" : ver) + "　タップして更新", open);
    }
}
