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
                Updater.silentUpdate(getApplicationContext(), true);
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
