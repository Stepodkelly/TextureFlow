package com.textureflow.notifications;

import android.content.ComponentName;
import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import com.textureflow.actions.ActionCommand;
import com.textureflow.actions.ActionReceipt;
import com.textureflow.actions.ConfirmedProposal;
import com.textureflow.actions.LiveActionRegistry;
import com.textureflow.actions.NotificationControl;
import com.textureflow.data.EventWriteResult;
import com.textureflow.data.StoredNotificationEvent;
import com.textureflow.policy.NotificationIngestionPolicy;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class TextureNotificationListenerService extends NotificationListenerService
        implements NotificationControl {
    private static final long CALLBACK_RECONCILE_DELAY_MS = 2_000L;
    private static final Handler REBIND_HANDLER = new Handler(Looper.getMainLooper());
    private static final Object REBIND_LOCK = new Object();
    private static final AtomicInteger REBIND_ATTEMPTS = new AtomicInteger();
    private static volatile WeakReference<TextureNotificationListenerService> activeService =
            new WeakReference<>(null);
    private static Runnable pendingRebind;

    private final AtomicBoolean listenerConnected = new AtomicBoolean(false);
    private final AtomicBoolean reconcileQueued = new AtomicBoolean(false);
    private HandlerThread workerThread;
    private Handler worker;
    private NotificationRuntime runtime;
    private NotificationNormalizer normalizer;
    private NotificationIngestionPolicy ingestionPolicy;

    @Override
    public void onCreate() {
        super.onCreate();
        runtime = NotificationRuntime.get(this);
        normalizer = new NotificationNormalizer();
        ingestionPolicy = new NotificationIngestionPolicy(this);
        workerThread = new HandlerThread("textureflow-notification-worker", Process.THREAD_PRIORITY_BACKGROUND);
        workerThread.start();
        worker = new Handler(workerThread.getLooper());
        activeService = new WeakReference<>(this);
        NotificationHealthJobService.schedule(this);
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        listenerConnected.set(true);
        activeService = new WeakReference<>(this);
        cancelRebindRetries();
        postReliable(() -> {
            runtime.health().connected(System.currentTimeMillis());
            reconcileActiveNotifications("listener-connected");
        });
    }

    @Override
    public void onListenerDisconnected() {
        listenerConnected.set(false);
        postReliable(() -> runtime.health().disconnected(
                System.currentTimeMillis(), "Notification listener disconnected"));
        requestRebindWithBackoff(getApplicationContext());
        super.onListenerDisconnected();
    }

    @Override
    public void onNotificationPosted(StatusBarNotification statusBarNotification) {
        // Android invokes this on the main thread on modern releases. Keep it to one queue handoff.
        postReliable(() -> handlePosted(statusBarNotification));
        scheduleDebouncedReconciliation();
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification statusBarNotification) {
        postReliable(() -> handleRemoved(statusBarNotification));
        scheduleDebouncedReconciliation();
    }

    @Override
    public void onNotificationRemoved(
            StatusBarNotification statusBarNotification, RankingMap rankingMap, int reason) {
        postReliable(() -> handleRemoved(statusBarNotification));
        scheduleDebouncedReconciliation();
    }

    @Override
    public void onDestroy() {
        listenerConnected.set(false);
        if (activeService.get() == this) activeService = new WeakReference<>(null);
        if (worker != null) {
            worker.post(() -> runtime.health().disconnected(
                    System.currentTimeMillis(), "Notification listener destroyed"));
        }
        if (workerThread != null) workerThread.quitSafely();
        requestRebindWithBackoff(getApplicationContext());
        super.onDestroy();
    }

    @Override
    public boolean isAvailable() {
        return listenerConnected.get();
    }

    @Override
    public void dismiss(String notificationKey) {
        if (!isAvailable()) throw new IllegalStateException("Notification listener is disconnected");
        cancelNotification(notificationKey);
    }

    @Override
    public void snooze(String notificationKey, long durationMs) {
        if (!isAvailable()) throw new IllegalStateException("Notification listener is disconnected");
        snoozeNotification(notificationKey, durationMs);
    }

    public static ActionReceipt executeConfirmed(
            Context context, ActionCommand command, ConfirmedProposal confirmation) {
        TextureNotificationListenerService service = activeService.get();
        NotificationControl control = service == null ? UNAVAILABLE_CONTROL : service;
        return NotificationRuntime.get(context).execute(command, confirmation, control);
    }

    public static void requestHealthReconciliation(Context context) {
        TextureNotificationListenerService service = activeService.get();
        if (service != null && service.listenerConnected.get()) {
            service.postReliable(() -> service.reconcileActiveNotifications("periodic-health"));
        } else {
            requestRebindWithBackoff(context.getApplicationContext());
        }
    }

    public static void requestRebindNow(Context context) {
        Context application = context.getApplicationContext();
        if (hasConnectedService()) {
            cancelRebindRetries();
            return;
        }
        try {
            NotificationListenerService.requestRebind(component(application));
        } catch (RuntimeException ignored) {
            // The persisted health job retries even if the framework rejects this immediate request.
        } finally {
            requestRebindWithBackoff(application);
        }
    }

    private void handlePosted(StatusBarNotification statusBarNotification) {
        long now = System.currentTimeMillis();
        runtime.health().callback(now);
        try {
            NotificationSnapshot snapshot = NotificationSnapshot.capture(statusBarNotification);
            if (!ingestionPolicy.shouldIngest(snapshot)) return;
            NormalizedNotification normalized = normalizer.normalize(this, snapshot, runtime.getDeviceId());
            EventWriteResult write = runtime.notifications().upsertActive(normalized, now);
            StoredNotificationEvent stored = write.getEvent();
            runtime.liveActions().put(runtime.liveActions().createEntry(
                    stored.getEventId(), stored.getVersion(), stored.getActionFingerprint(), snapshot));
        } catch (RuntimeException failure) {
            runtime.health().failed(now, failure);
            scheduleDebouncedReconciliation();
        }
    }

    private void handleRemoved(StatusBarNotification statusBarNotification) {
        long now = System.currentTimeMillis();
        runtime.health().callback(now);
        try {
            if (statusBarNotification == null) return;
            String eventId = ContentFingerprint.eventId(
                    runtime.getDeviceId(), statusBarNotification.getPackageName(), statusBarNotification.getKey());
            runtime.notifications().markRemoved(eventId, now);
            runtime.liveActions().remove(eventId);
        } catch (RuntimeException failure) {
            runtime.health().failed(now, failure);
            scheduleDebouncedReconciliation();
        }
    }

    private void reconcileActiveNotifications(String reason) {
        reconcileQueued.set(false);
        long now = System.currentTimeMillis();
        try {
            StatusBarNotification[] active = getActiveNotifications();
            if (active == null) active = new StatusBarNotification[0];

            Set<String> activeIds = new HashSet<>();
            Map<String, LiveActionRegistry.Entry> rebuilt = new HashMap<>();
            for (StatusBarNotification statusBarNotification : active) {
                try {
                    NotificationSnapshot snapshot = NotificationSnapshot.capture(statusBarNotification);
                    if (!ingestionPolicy.shouldIngest(snapshot)) continue;
                    NormalizedNotification normalized = normalizer.normalize(this, snapshot, runtime.getDeviceId());
                    EventWriteResult write = runtime.notifications().upsertActive(normalized, now);
                    StoredNotificationEvent stored = write.getEvent();
                    activeIds.add(stored.getEventId());
                    rebuilt.put(stored.getEventId(), runtime.liveActions().createEntry(
                            stored.getEventId(), stored.getVersion(), stored.getActionFingerprint(), snapshot));
                } catch (RuntimeException malformedNotification) {
                    // Keep reconciling other entries, but do not mark a previously known malformed item removed.
                    String eventId = safeEventId(statusBarNotification);
                    if (eventId != null) activeIds.add(eventId);
                    runtime.health().failed(now, malformedNotification);
                }
            }

            runtime.notifications().markMissingRemoved(activeIds, now);
            runtime.liveActions().replaceAll(rebuilt);
            runtime.health().reconciled(now, activeIds.size());
        } catch (SecurityException | IllegalStateException listenerFailure) {
            runtime.health().failed(now, listenerFailure);
            requestRebindWithBackoff(getApplicationContext());
        } catch (RuntimeException unexpected) {
            runtime.health().failed(now, unexpected);
            requestRebindWithBackoff(getApplicationContext());
        }
    }

    private String safeEventId(StatusBarNotification item) {
        try {
            return item == null ? null : ContentFingerprint.eventId(
                    runtime.getDeviceId(), item.getPackageName(), item.getKey());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private void scheduleDebouncedReconciliation() {
        if (worker == null || !reconcileQueued.compareAndSet(false, true)) return;
        if (!worker.postDelayed(() -> reconcileActiveNotifications("callback-debounce"),
                CALLBACK_RECONCILE_DELAY_MS)) {
            reconcileQueued.set(false);
            requestRebindWithBackoff(getApplicationContext());
        }
    }

    private void postReliable(Runnable task) {
        if (worker == null || !worker.post(task)) {
            requestRebindWithBackoff(getApplicationContext());
        }
    }

    private static void requestRebindWithBackoff(Context context) {
        Context application = context.getApplicationContext();
        if (hasConnectedService()) {
            cancelRebindRetries();
            return;
        }
        synchronized (REBIND_LOCK) {
            if (pendingRebind != null) return;
            long delay = NotificationRebindPolicy.delayMillis(REBIND_ATTEMPTS.getAndIncrement());
            pendingRebind = () -> runScheduledRebind(application);
            if (!REBIND_HANDLER.postDelayed(pendingRebind, delay)) pendingRebind = null;
        }
    }

    private static void runScheduledRebind(Context context) {
        synchronized (REBIND_LOCK) {
            pendingRebind = null;
        }
        requestRebindNow(context);
    }

    private static boolean hasConnectedService() {
        TextureNotificationListenerService service = activeService.get();
        return service != null && service.listenerConnected.get();
    }

    private static void cancelRebindRetries() {
        synchronized (REBIND_LOCK) {
            if (pendingRebind != null) REBIND_HANDLER.removeCallbacks(pendingRebind);
            pendingRebind = null;
            REBIND_ATTEMPTS.set(0);
        }
    }

    private static ComponentName component(Context context) {
        return new ComponentName(context, TextureNotificationListenerService.class);
    }

    private static final NotificationControl UNAVAILABLE_CONTROL = new NotificationControl() {
        @Override public boolean isAvailable() { return false; }
        @Override public void dismiss(String notificationKey) { throw new IllegalStateException("Disconnected"); }
        @Override public void snooze(String notificationKey, long durationMs) { throw new IllegalStateException("Disconnected"); }
    };
}
