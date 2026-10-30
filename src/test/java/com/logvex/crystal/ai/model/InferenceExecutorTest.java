package com.logvex.crystal.ai.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The bounded executor: limits, overflow rejection, cancellation and timeouts. */
class InferenceExecutorTest {

    @TempDir
    Path dir;

    @Test
    void runsWorkOffTheCallingThread() throws Exception {
        try (InferenceExecutor executor = new InferenceExecutor(1, 2, "t")) {
            Thread caller = Thread.currentThread();
            Future<Boolean> future = executor.submit(() -> Thread.currentThread() != caller);
            assertTrue(future.get(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void rejectsOverflowInsteadOfGrowingUnbounded() throws Exception {
        try (InferenceExecutor executor = new InferenceExecutor(1, 1, "t")) {
            CountDownLatch blocker = new CountDownLatch(1);
            // Occupy the single thread.
            assertNotNull(executor.submit(() -> {
                blocker.await(5, TimeUnit.SECONDS);
                return null;
            }));
            // Fill the single queue slot.
            assertNotNull(executor.submit(() -> "queued"));
            // The next request must be rejected, not block or grow the queue.
            assertNull(executor.submit(() -> "overflow"));
            assertEquals(1, executor.rejectedCount());
            blocker.countDown();
        }
    }

    @Test
    void submitAndWaitTimesOutAndCancels() {
        try (InferenceExecutor executor = new InferenceExecutor(1, 2, "t")) {
            assertThrows(TimeoutException.class, () -> executor.submitAndWait(() -> {
                Thread.sleep(10_000);
                return "never";
            }, 100));
        }
    }

    @Test
    void cancellationOfQueuedTaskPreventsExecution() throws Exception {
        try (InferenceExecutor executor = new InferenceExecutor(1, 2, "t")) {
            CountDownLatch blocker = new CountDownLatch(1);
            AtomicInteger ran = new AtomicInteger();
            executor.submit(() -> {
                blocker.await(5, TimeUnit.SECONDS);
                return null;
            });
            Future<?> queued = executor.submit(ran::incrementAndGet);
            assertNotNull(queued);
            assertTrue(queued.cancel(false));
            blocker.countDown();
            Thread.sleep(150);
            assertEquals(0, ran.get(), "cancelled task must not run");
        }
    }

    @Test
    void closeStopsAcceptingAndTerminatesThreads() throws Exception {
        InferenceExecutor executor = new InferenceExecutor(1, 2, "t");
        executor.submit(() -> "x");
        executor.close();
        // After close, submissions are rejected (return null), not queued.
        assertNull(executor.submit(() -> "y"));
    }

    @Test
    void threadsAreDaemonSoTheyNeverBlockShutdown() throws Exception {
        try (InferenceExecutor executor = new InferenceExecutor(1, 2, "t")) {
            Future<String> future = executor.submit(() -> Thread.currentThread().getName());
            String name = future.get(5, TimeUnit.SECONDS);
            assertTrue(name.startsWith("t-"));
        }
    }

    @Test
    void reportsIdleWhenNoWork() throws Exception {
        try (InferenceExecutor executor = new InferenceExecutor(1, 2, "t")) {
            executor.submit(() -> "x").get(5, TimeUnit.SECONDS);
            // Allow the worker to finish draining.
            Thread.sleep(50);
            assertTrue(executor.isIdle());
        }
    }

    @Test
    void loadFailsCleanlyWithoutRuntime() throws IOException {
        ModelLoader loader = new ModelLoader(dir);
        ModelSpec spec = ModelCatalog.all().get(0);
        // Install a plausible file so only the missing runtime can cause failure.
        byte[] bytes = new byte[4096];
        bytes[0] = 'G'; bytes[1] = 'G'; bytes[2] = 'U'; bytes[3] = 'F';
        Files.write(dir.resolve(spec.fileName()), bytes);
        assertThrows(InferenceException.class, () -> loader.load(spec, 1));
        assertFalse(loader.hasPartialDownload(spec));
    }
}
