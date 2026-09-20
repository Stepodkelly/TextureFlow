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
import com.textureflow.bank.BankEntry;
import com.textureflow.bank.BankKey;
import com.textureflow.data.EventWriteResult;
import com.textureflow.data.ListenerHealthStore;
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
    private static final long SELF_HEALTH_INTERVAL_MS = 6_000L;
    private static final Handler REBIND_HANDLER = new Handler(Looper.getMainLooper());
    private static final Object REBIND_LOCK = new Object();
    private static final AtomicInteger REBIND_ATTEMPTS = new AtomicInteger();
    private static final long FORCE_REBIND_COOLDOWN_MS = 12_000L;
    private static volatile long lastForcedRebindAt;
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
    private final Runnable selfHealthCheck = new Runnable() {
        @Override
        public void run() {
            if (listenerConnected.get()) {
                reconcileActiveNotifications("listener-self-health");
            }
            if (worker != null) worker.postDelayed(this, SELF_HEALTH_INTERVAL_MS);
        }
    };

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
        ListenerHealthStore.Snapshot health = runtime.health().read();
        if (health.connected && !listenerConnected.get()) {
            runtime.health().markStale(
                    System.currentTimeMillis(), "service created with sticky connected flag");
        }
        NotificationHealthJobService.schedule(this);
        NotificationWatchdogScheduler.schedule(this);
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
            worker.removeCallbacks(selfHealthCheck);
            worker.postDelayed(selfHealthCheck, SELF_HEALTH_INTERVAL_MS);
        });
    }

    @Override
    public void onListenerDisconnected() {
        listenerConnected.set(false);
        if (worker != null) worker.removeCallbacks(selfHealthCheck);
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
            worker.removeCallbacks(selfHealthCheck);
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
        if (hasLiveConnection()) {
            long now = System.currentTimeMillis();
            ListenerHealthStore.Snapshot health =
                    NotificationRuntime.get(application).health().read();
            // Live flag alone is not proof of a healthy bind. Do not cancel in-flight recovery
            // when health is already stale / locked out — that was canceling force paths.
            if (ListenerHealthPolicy.needsForceRestart(health, now, true)) {
                forceStaleRebind(application);
                return;
            }
            if (!ListenerHealthPolicy.isFresh(health, now)) {
                requestHealthReconciliation(application);
                return;
            }
            cancelRebindRetries();
            requestHealthReconciliation(application);
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

    /** Tears down a framework connection proven stale by an external freshness watchdog. */
    public static void forceStaleRebind(Context context) {
        Context application = context.getApplicationContext();
        long now = System.currentTimeMillis();
        NotificationRuntime.get(application).health().markStale(now, "force stale rebind");
        // Always drop the in-process live lie first so requestRebind*/UI cannot no-op on it,
        // even when the aggressive unbind path is cooldown-skipped.
        TextureNotificationListenerService service = activeService.get();
        if (service != null) {
            service.listenerConnected.set(false);
            if (service.worker != null) service.worker.removeCallbacks(service.selfHealthCheck);
        }
        synchronized (REBIND_LOCK) {
            if (now - lastForcedRebindAt < FORCE_REBIND_COOLDOWN_MS) {
                // Cooldown must not become a total no-op: keep backoff recovery armed.
                requestRebindWithBackoff(application);
                return;
            }
            lastForcedRebindAt = now;
        }
        if (service != null) {
            try {
                service.requestUnbind();
            } catch (RuntimeException ignored) {
                // The delayed framework rebind below is still attempted.
            }
        }
        REBIND_HANDLER.postDelayed(() -> {
            try {
                NotificationListenerService.requestRebind(component(application));
            } catch (RuntimeException ignored) {
                // fall through to backoff
            }
            requestRebindWithBackoff(application);
        }, 750L);
    }

    /** True when any retained service instance currently reports a live listener connection. */
    public static boolean hasLiveConnection() {
        TextureNotificationListenerService service = activeService.get();
        return service != null && service.listenerConnected.get();
    }

    private void handlePosted(StatusBarNotification statusBarNotification) {
        long now = System.currentTimeMillis();
        try {
            NotificationSnapshot snapshot = NotificationSnapshot.capture(statusBarNotification);
            // Own FGS / system / service noise must not forge callback freshness — Core polls
            // republish the foreground notification on every healthy loop and would keep the
            // reader "active" forever while third-party posts are dead.
            if (!ingestionPolicy.shouldIngest(snapshot)) return;
            runtime.health().callback(now);
            NormalizedNotification normalized = normalizer.normalize(this, snapshot, runtime.getDeviceId());
            EventWriteResult write = runtime.notifications().upsertActive(normalized, now);
            StoredNotificationEvent stored = write.getEvent();
            runtime.liveActions().put(runtime.liveActions().createEntry(
                    stored.getEventId(), stored.getVersion(), stored.getActionFingerprint(), snapshot));
            if (normalized.getCapabilities().contains("REPLY")) {
                try {
                    String peer = peerKey(normalized);
                    BankEntry banked = new BankEntry(
                            new BankKey(peer, normalized.getPackageName()),
                            stored.getEventId(),
                            stored.getNotificationKey(),
                            stored.getSenderName(),
                            stored.getConversationLabel(),
                            stored.getBody(),
                            now);
                    // adoptAndArm isolates snooze failures; never mark health failed here.
                    runtime.bank().adoptAndArm(this, banked);
                } catch (RuntimeException bankFailure) {
                    // Bank persistence must never poison listener health or drop the notification.
                }
            }
        } catch (RuntimeException failure) {
            runtime.health().failed(now, failure);
            scheduleDebouncedReconciliation();
        }
    }

    private void handleRemoved(StatusBarNotification statusBarNotification) {
        long now = System.currentTimeMillis();
        try {
            if (statusBarNotification == null) return;
            NotificationSnapshot snapshot = NotificationSnapshot.capture(statusBarNotification);
            if (!ingestionPolicy.shouldIngest(snapshot)) return;
            runtime.health().callback(now);
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
            // Successful getActiveNotifications is not proof of a live callback pipe.
            // If callbacks are past FORCE (or never arrived after connect grace), treat as lockout.
            ListenerHealthStore.Snapshot after = runtime.health().read();
            if (ListenerHealthPolicy.probeLooksLockedOut(after, now, listenerConnected.get())) {
                runtime.health().markStale(now, "reconcile without fresh callbacks");
                forceStaleRebind(getApplicationContext());
            }
        } catch (SecurityException listenerFailure) {
            runtime.health().failed(now, listenerFailure);
            runtime.health().markStale(now, "listener security failure");
            forceStaleRebind(getApplicationContext());
        } catch (IllegalStateException listenerFailure) {
            runtime.health().failed(now, listenerFailure);
            runtime.health().markStale(now, "listener illegal state");
            // Backoff is a no-op while hasLiveConnection() is true; must tear down the live lie.
            forceStaleRebind(getApplicationContext());
        } catch (RuntimeException unexpected) {
            runtime.health().failed(now, unexpected);
            runtime.health().markStale(now, "listener reconcile failure");
            forceStaleRebind(getApplicationContext());
        }
    }

    private static String peerKey(NormalizedNotification normalized) {
        if (normalized.getSenderName() != null && !normalized.getSenderName().trim().isEmpty()) {
            return normalized.getSenderName().trim();
        }
        if (normalized.getConversationLabel() != null
                && !normalized.getConversationLabel().trim().isEmpty()) {
            return normalized.getConversationLabel().trim();
        }
        return normalized.getPackageName();
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
        if (hasLiveConnection()) {
            long now = System.currentTimeMillis();
            ListenerHealthStore.Snapshot health =
                    NotificationRuntime.get(application).health().read();
            if (ListenerHealthPolicy.needsForceRestart(health, now, true)) {
                forceStaleRebind(application);
                return;
            }
            // Only clear recovery when the bind is actually fresh.
            if (ListenerHealthPolicy.isFresh(health, now)) {
                cancelRebindRetries();
            }
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
