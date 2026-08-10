package com.textureflow.notifications;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class NotificationHealthJobService extends JobService {
    private static final int JOB_ID = 0x54464E; // TFN
    private static final long INTERVAL_MS = 15 * 60_000L;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    public static void schedule(Context context) {
        try {
            JobScheduler scheduler = context.getSystemService(JobScheduler.class);
            if (scheduler == null) return;
            JobInfo job = new JobInfo.Builder(
                    JOB_ID, new ComponentName(context, NotificationHealthJobService.class))
                    .setPeriodic(INTERVAL_MS)
                    .setPersisted(true)
                    .build();
            scheduler.schedule(job);
        } catch (RuntimeException ignored) {
            // Manifest integration may not be complete yet; connection reconciliation still runs.
        }
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        EXECUTOR.execute(() -> {
            try {
                TextureNotificationListenerService.requestHealthReconciliation(getApplicationContext());
            } finally {
                jobFinished(params, false);
            }
        });
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}
