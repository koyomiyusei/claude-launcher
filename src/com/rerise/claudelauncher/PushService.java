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
        try {
            TalkSync.syncAndNotify(getApplicationContext());
        } catch (Exception ignored) {
        }
    }

    @Override
    public void onNewToken(String token) {
        Push.uploadToken(getApplicationContext(), token);
    }
}
