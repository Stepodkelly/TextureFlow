package com.textureflow.notifications;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;
import android.text.TextUtils;

import com.textureflow.data.ListenerHealthStore;
import com.textureflow.intelligence.ledger.IntelligenceLedger;

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

    static long retentionCutoff(long now) {
        return now - IntelligenceLedger.RETENTION_MILLIS;
    }

    static boolean hasNotificationAccess(Context context) {
        String enabled = Settings.Secure.getString(
                context.getContentResolver(), "enabled_notification_listeners");
        if (enabled == null || enabled.isEmpty()) return false;
        ComponentName expected = new ComponentName(
                context, TextureNotificationListenerService.class);
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabled);
        while (splitter.hasNext()) {
            ComponentName candidate = ComponentName.unflattenFromString(splitter.next());
            if (expected.equals(candidate)) return true;
        }
        return false;
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        EXECUTOR.execute(() -> {
            try {
                Context application = getApplicationContext();
                long now = System.currentTimeMillis();
                NotificationRuntime runtime = NotificationRuntime.get(application);
                try {
                    runtime.ledger().purgeOlderThan(retentionCutoff(now));
                    runtime.modelLifecycle().unloadIfIdle(now);
                } catch (RuntimeException ignored) {
                    // Retention / idle unload must not skip listener health.
                }
                if (!hasNotificationAccess(application)) {
                    runtime.health().markStale(now, "notification access revoked");
                    return;
                }
                ListenerHealthStore.Snapshot health = runtime.health().read();
                boolean live = TextureNotificationListenerService.hasLiveConnection();
                if (ListenerHealthPolicy.needsForceRestart(health, now, live)) {
                    TextureNotificationListenerService.forceStaleRebind(application);
                } else {
                    TextureNotificationListenerService.requestHealthReconciliation(application);
                }
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
