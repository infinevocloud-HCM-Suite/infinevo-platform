package com.infinevo.payroll.taxcalc.recalc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** D-147: the taxRecalc pool no longer drops a job silently when its queue is full. */
class TaxRecalcConfigTest {

    @Test
    @DisplayName("The production pool carries the retrying rejection handler, not the default AbortPolicy")
    void poolUsesRetryingRejection() {
        ThreadPoolTaskExecutor ex = (ThreadPoolTaskExecutor) new TaxRecalcConfig().taxRecalcExecutor();
        try {
            assertThat(ex.getThreadPoolExecutor().getRejectedExecutionHandler())
                    .isInstanceOf(TaxRecalcConfig.RetryingRejection.class);
        } finally {
            ex.shutdown();
        }
    }

    @Test
    @DisplayName("A job rejected by a full queue waits for room and then runs")
    void rejectedJobWaitsAndRuns() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1,
                1,
                0,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1),
                new TaxRecalcConfig.RetryingRejection(5, TimeUnit.SECONDS));
        try {
            pool.execute(() -> await(release)); // occupies the only worker
            pool.execute(() -> {}); // fills the queue
            CountDownLatch ran = new CountDownLatch(1);
            Thread releaser = new Thread(() -> {
                sleep(200);
                release.countDown();
            });
            releaser.start();

            pool.execute(ran::countDown); // rejected first, then queued once room frees

            assertThat(ran.await(5, TimeUnit.SECONDS))
                    .as("the rejected job ran")
                    .isTrue();
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("A job still without room after the wait is dropped with a log line, never thrown at the caller")
    void stillFullIsDroppedWithoutThrowing() {
        CountDownLatch release = new CountDownLatch(1);
        ThreadPoolExecutor pool = new ThreadPoolExecutor(
                1,
                1,
                0,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1),
                new TaxRecalcConfig.RetryingRejection(50, TimeUnit.MILLISECONDS));
        AtomicBoolean ran = new AtomicBoolean(false);
        try {
            pool.execute(() -> await(release));
            pool.execute(() -> {});

            pool.execute(() -> ran.set(true)); // must not throw

            assertThat(ran.get()).isFalse();
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
