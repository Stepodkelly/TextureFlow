package com.textureflow.connection;

import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Serialized connection sidecar with a dedicated watchdog. Every task catches all throwables and
 * reschedules itself; a wedged network loop is replaced without trusting the old thread to recover.
 */
public final class ConnectionEngine implements AutoCloseable {
    private static final long WATCHDOG_TICK_MS = 5_000L;

    private final Object lifecycleLock = new Object();
    private final ConnectionConfig config;
    private final RemoteGateway gateway;
    private final OutboxCoordinator outbox;
    private final CommandProcessor commands;
    private final ConnectionClock clock;
    private final BackoffPolicy connectionBackoff;
    private final ConnectionWatchdog watchdog;
    private final ConnectionStateMachine states;
    private final ConnectionObserver observer;
    private final AtomicLong generation = new AtomicLong();

    private volatile boolean running;
    private volatile boolean registered;
    private volatile boolean operationInFlight;
    private volatile boolean loopScheduled;
    private volatile long lastPollAttemptAt;
    private volatile long nextHeartbeatAt;
    private volatile int consecutiveFailures;
    private ScheduledExecutorService loopExecutor;
    private ScheduledExecutorService watchdogExecutor;
    private ScheduledFuture<?> loopFuture;

    public ConnectionEngine(
            ConnectionConfig config,
            RemoteGateway gateway,
            OutboxCoordinator outbox,
            CommandProcessor commands,
            ConnectionClock clock,
            BackoffPolicy connectionBackoff,
            ConnectionStateMachine states,
            ConnectionObserver observer) {
        this.config = config;
        this.gateway = gateway;
        this.outbox = outbox;
        this.commands = commands;
        this.clock = clock;
        this.connectionBackoff = connectionBackoff;
        this.states = states;
        this.observer = observer == null ? ConnectionObserver.NONE : observer;
        this.watchdog = new ConnectionWatchdog(config.watchdogStaleAfterMs(), 5_000L);
    }

    public void start() {
        synchronized (lifecycleLock) {
            if (running) return;
            running = true;
            registered = false;
            consecutiveFailures = 0;
            long now = clock.nowMillis();
            lastPollAttemptAt = now;
            nextHeartbeatAt = now;
            publish(states.start(now));
            loopExecutor = newLoopExecutor();
            watchdogExecutor = Executors.newSingleThreadScheduledExecutor(
                    namedFactory("textureflow-connection-watchdog"));
            scheduleLoopLocked(0, generation.incrementAndGet());
            scheduleWatchdogLocked(WATCHDOG_TICK_MS);
        }
    }

    public ConnectionStateMachine.Snapshot snapshot() {
        return states.snapshot();
    }

    public void stop() {
        synchronized (lifecycleLock) {
            if (!running) return;
            running = false;
            publish(states.beginStop(clock.nowMillis()));
            generation.incrementAndGet();
            if (loopFuture != null) loopFuture.cancel(true);
            if (loopExecutor != null) loopExecutor.shutdownNow();
            if (watchdogExecutor != null) watchdogExecutor.shutdownNow();
            loopScheduled = false;
            operationInFlight = false;
            publish(states.stopped(clock.nowMillis()));
        }
    }

    @Override
    public void close() {
        stop();
    }

    private void runLoop(long expectedGeneration) {
        loopScheduled = false;
        if (!running || expectedGeneration != generation.get()) return;
        long delay = config.healthyPollIntervalMs();
        operationInFlight = true;
        lastPollAttemptAt = clock.nowMillis();
        try {
            if (!registered) {
                publish(states.registering(clock.nowMillis()));
                gateway.registerDevice();
                registered = true;
                nextHeartbeatAt = clock.nowMillis();
            }
            long now = clock.nowMillis();
            if (now >= nextHeartbeatAt) {
                gateway.heartbeat();
                nextHeartbeatAt = now + config.heartbeatIntervalMs();
            }
            outbox.drain(now, Math.random());
            commands.pollAndProcess(clock.nowMillis(), 10);
            lastPollAttemptAt = clock.nowMillis();
            consecutiveFailures = 0;
            publish(states.online(lastPollAttemptAt));
        } catch (Throwable failure) {
            consecutiveFailures++;
            registered = false;
            delay = connectionBackoff.delayMillis(consecutiveFailures, Math.random());
            publish(states.failure(clock.nowMillis(), safeMessage(failure)));
        } finally {
            operationInFlight = false;
            synchronized (lifecycleLock) {
                if (running && expectedGeneration == generation.get()) {
                    scheduleLoopLocked(delay, expectedGeneration);
                }
            }
        }
    }

    private void runWatchdog() {
        try {
            if (!running) return;
            ConnectionWatchdog.Decision decision = watchdog.inspect(
                    clock.nowMillis(), lastPollAttemptAt, loopScheduled, operationInFlight);
            if (decision.action() == ConnectionWatchdog.Action.WAKE) {
                synchronized (lifecycleLock) {
                    if (running && !loopScheduled) scheduleLoopLocked(0, generation.get());
                }
            } else if (decision.action() == ConnectionWatchdog.Action.RESTART) {
                recoverLoop(decision.reason());
            }
        } catch (Throwable failure) {
            publish(states.degraded(clock.nowMillis(), "Watchdog error: " + safeMessage(failure)));
        } finally {
            synchronized (lifecycleLock) {
                if (running) scheduleWatchdogLocked(WATCHDOG_TICK_MS);
            }
        }
    }

    private void recoverLoop(String reason) {
        synchronized (lifecycleLock) {
            if (!running) return;
            publish(states.watchdogRecovery(clock.nowMillis(), reason));
            long nextGeneration = generation.incrementAndGet();
            if (loopFuture != null) loopFuture.cancel(true);
            if (loopExecutor != null) loopExecutor.shutdownNow();
            loopExecutor = newLoopExecutor();
            registered = false;
            operationInFlight = false;
            loopScheduled = false;
            lastPollAttemptAt = clock.nowMillis();
            scheduleLoopLocked(0, nextGeneration);
        }
    }

    private void scheduleLoopLocked(long delayMs, long expectedGeneration) {
        if (!running || loopScheduled) return;
        loopScheduled = true;
        try {
            loopFuture = loopExecutor.schedule(
                    () -> runLoop(expectedGeneration), Math.max(0, delayMs), TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException rejected) {
            loopScheduled = false;
            loopExecutor = newLoopExecutor();
            long nextGeneration = generation.incrementAndGet();
            loopScheduled = true;
            loopFuture = loopExecutor.schedule(
                    () -> runLoop(nextGeneration), 0, TimeUnit.MILLISECONDS);
        }
    }

    private void scheduleWatchdogLocked(long delayMs) {
        try {
            watchdogExecutor.schedule(this::runWatchdog, delayMs, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException ignored) {
            if (running) publish(states.degraded(clock.nowMillis(), "Watchdog scheduler rejected recovery"));
        }
    }

    private ScheduledExecutorService newLoopExecutor() {
        return Executors.newSingleThreadScheduledExecutor(namedFactory("textureflow-connection-loop"));
    }

    private static ThreadFactory namedFactory(String name) {
        return task -> {
            Thread thread = new Thread(task, name);
            thread.setDaemon(false);
            thread.setUncaughtExceptionHandler((ignored, error) -> {});
            return thread;
        };
    }

    private void publish(ConnectionStateMachine.Snapshot snapshot) {
        try {
            observer.onState(snapshot);
        } catch (RuntimeException ignored) {
            // UI or notification observers must not kill the connection engine.
        }
    }

    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        if (message == null || message.trim().isEmpty()) return failure.getClass().getSimpleName();
        return message.length() <= 240 ? message : message.substring(0, 240);
    }
}
