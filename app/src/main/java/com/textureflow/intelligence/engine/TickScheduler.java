package com.textureflow.intelligence.engine;

import com.textureflow.intelligence.api.EventSignal;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * One worker thread. Per-person coalescing: while a person is queued, only the
 * newest {@link EventSignal} is kept.
 */
public final class TickScheduler {
    private final ExecutorService executor;
    private final Object lock = new Object();
    private final Map<String, EventSignal> pending = new HashMap<>();
    private final Set<String> inflight = new HashSet<>();

    public TickScheduler() {
        this(Executors.newSingleThreadExecutor(attentionThreads()));
    }

    public TickScheduler(ExecutorService executor) {
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public void enqueue(EventSignal signal, Consumer<EventSignal> handler) {
        Objects.requireNonNull(signal, "signal");
        Objects.requireNonNull(handler, "handler");
        String key = coalesceKey(signal);
        synchronized (lock) {
            pending.put(key, signal);
            if (inflight.add(key)) {
                executor.execute(() -> drain(key, handler));
            }
        }
    }

    public void execute(Runnable task) {
        executor.execute(Objects.requireNonNull(task, "task"));
    }

    public void awaitIdle(long timeout, TimeUnit unit)
            throws InterruptedException, TimeoutException {
        Future<?> done = executor.submit(() -> { });
        try {
            done.get(timeout, unit);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            throw new IllegalStateException(cause);
        }
    }

    public void shutdown() {
        executor.shutdownNow();
    }

    static String coalesceKey(EventSignal signal) {
        String personId = signal.getPersonId();
        if (personId != null && !personId.isEmpty()) {
            return personId;
        }
        return signal.getEventId();
    }

    private void drain(String key, Consumer<EventSignal> handler) {
        try {
            while (true) {
                EventSignal signal;
                synchronized (lock) {
                    signal = pending.remove(key);
                    if (signal == null) {
                        inflight.remove(key);
                        return;
                    }
                }
                try {
                    handler.accept(signal);
                } catch (RuntimeException ignored) {
                    // A failed tick must not stall later signals for this person.
                }
            }
        } catch (Error error) {
            synchronized (lock) {
                inflight.remove(key);
            }
            throw error;
        }
    }

    private static ThreadFactory attentionThreads() {
        return runnable -> {
            Thread thread = new Thread(runnable, "textureflow-attention");
            thread.setDaemon(true);
            return thread;
        };
    }
}
