package com.rerise.claudelauncher;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

/** 15分おきに notices.json を見に行く定期ジョブ（Androidの最短間隔が15分） */
public class NoticeJob extends JobService {

    private static final int JOB_ID = 1001;
    public static final long INTERVAL_MS = 15 * 60 * 1000L;

    public static void schedule(Context c) {
        JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js == null) return;
        if (!Notices.enabled(c)) {
            js.cancel(JOB_ID);
            return;
        }
        if (js.getPendingJob(JOB_ID) != null) return;
        JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(c, NoticeJob.class))
                .setPeriodic(INTERVAL_MS)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .build();
        try {
            js.schedule(job);
        } catch (Exception ignored) {
        }
    }

    public static void cancel(Context c) {
        JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (js != null) js.cancel(JOB_ID);
    }

    @Override
    public boolean onStartJob(final JobParameters params) {
        new Thread(new Runnable() {
            public void run() {
                try {
                    Notices.checkAndNotify(getApplicationContext());
                } catch (Exception ignored) {
                }
                jobFinished(params, false);
            }
        }).start();
        return true; // 作業は別スレッドで続く
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true; // 失敗したら次の周期で拾い直す
    }
}
