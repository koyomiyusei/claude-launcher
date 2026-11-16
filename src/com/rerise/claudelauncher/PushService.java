package com.rerise.claudelauncher;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

/**
 * プッシュ通知の受け口。
 * 届くのは「見に来て」という合図だけ（中身は入っていない）。受け取ったらサーバーから
 * トークを取り直して、新しく返事が来たものを通知する。
 */
public class PushService extends FirebaseMessagingService {

    @Override
    public void onMessageReceived(RemoteMessage message) {
        // 「新しい版が出た」の合図なら、その場で落として入れる
        if ("update".equals(message.getData().get("kind"))) {
            try {
                // 勝手に入れ始めると画面に割り込むので、通知だけ出す（タップしたら入る）
                Updater.silentUpdate(getApplicationContext(), true, false);
            } catch (Exception ignored) {
            }
            return;
        }
        // 中身つきの合図（v3.0〜のサーバー）なら、通信せずにその場で通知する
        if (message.getData().get("name") != null) {
            try {
                Talk shown = TalkSync.applyPush(getApplicationContext(), message.getData());
                if (shown != null) Notices.notifyTalk(getApplicationContext(), shown);
            } catch (Exception ignored) {
            }
            try {
                TalkSync.syncQuiet(getApplicationContext());
            } catch (Exception ignored) {
            }
            return;
        }
        // 端末が眠りから覚めた直後は通信がまだ通らないことがあるので、少し間をあけて3回まで試す
        boolean ok = false;
        long[] waits = {0, 3000, 6000};
        for (long w : waits) {
            try {
                if (w > 0) Thread.sleep(w);
                ok = TalkSync.syncAndNotify(getApplicationContext());
            } catch (Exception ignored) {
            }
            if (ok) break;
        }
        if (!ok) {
            try {
                TalkSync.notifyWithoutSync(getApplicationContext(), message.getData().get("id"));
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void onNewToken(String token) {
        Push.uploadToken(getApplicationContext(), token);
    }
}
