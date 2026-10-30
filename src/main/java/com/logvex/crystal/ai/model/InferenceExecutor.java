package com.logvex.crystal.ai.model;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The single place inference runs. A bounded {@link ThreadPoolExecutor} with one or two daemon
 * threads and a small, bounded queue guarantees the spec's resource limits: inference never
 * runs on the game thread, never spawns unbounded threads, and never queues without limit.
 *
 * <p>Overflow is handled by rejecting the newest request (so the oldest, still-relevant work
 * completes) rather than blocking the caller or growing the queue. {@link #submit} returns a
 * future the caller can cancel; a cancelled task that has not started is dropped, and a running
 * one is interrupted so the model can abandon it.
 */
public final class InferenceExecutor implements AutoCloseable {

    private final int queueLimit;
    private final ThreadPoolExecutor executor;
    private final AtomicInteger rejected = new AtomicInteger();
    private final AtomicInteger completed = new AtomicInteger();

    public InferenceExecutor(int threads, int queueLimit, String threadName) {
        int threadCount = Math.max(1, Math.min(2, threads));
        this.queueLimit = Math.max(0, queueLimit);
        AtomicInteger counter = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, threadName + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY + 1);
            return thread;
        };
        // A zero-length queue would mean "run immediately or reject", which we do not want;
        // keep at least one slot so a burst of two requests does not lose the second.
        int capacity = Math.max(1, this.queueLimit);
        this.executor = new ThreadPoolExecutor(threadCount, threadCount, 30L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(capacity), factory, new ThreadPoolExecutor.AbortPolicy());
        this.executor.allowCoreThreadTimeOut(true);
    }

    /**
     * Submits work. Returns a future to cancel, or {@code null} when the queue is full (the
     * caller should tell the user the request was dropped rather than block).
     */
    public <T> Future<T> submit(Callable<T> task) {
        if (task == null) {
            return null;
        }
        try {
            Future<T> future = executor.submit(() -> {
                try {
                    return task.call();
                } finally {
                    completed.incrementAndGet();
                }
            });
            return future;
        } catch (RejectedExecutionException e) {
            rejected.incrementAndGet();
            return null;
        }
    }

    /**
     * Runs {@code task} and waits at most {@code timeoutMillis} for it. On timeout the task is
     * cancelled and {@link TimeoutException} is thrown. Never runs the task on the calling thread.
     */
    public <T> T submitAndWait(Callable<T> task, long timeoutMillis) throws Exception {
        Future<T> future = submit(task);
        if (future == null) {
            throw new RejectedExecutionException("Inference queue is full");
        }
        try {
            return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw e;
        } catch (java.util.concurrent.CancellationException e) {
            throw e;
        }
    }

    public int queueDepth() {
        return executor.getQueue().size();
    }

    public int activeCount() {
        return executor.getActiveCount();
    }

    public int queueLimit() {
        return queueLimit;
    }

    public int rejectedCount() {
        return rejected.get();
    }

    public int completedCount() {
        return completed.get();
    }

    public boolean isIdle() {
        return executor.getActiveCount() == 0 && executor.getQueue().isEmpty();
    }

    @Override
    public void close() {
        executor.shutdownNow();
        try {
            // Wait for a running task to unwind before returning, so callers that tear down a
            // temp directory (or the game) do not race an in-flight disk write. The wait is
            // bounded: shutdownNow interrupts the worker, so this returns promptly.
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
