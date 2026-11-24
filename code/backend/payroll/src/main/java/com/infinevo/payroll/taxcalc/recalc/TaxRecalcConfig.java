package com.infinevo.payroll.taxcalc.recalc;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Configuration providing the named asynchronous executor for tax recalculations (W-33.3).
 *
 * <p>D-147: the pool's default {@code AbortPolicy} threw on a full queue and the recalculation was lost.
 * {@code CallerRunsPolicy} is no fix here: the caller is the committing thread inside its after-commit
 * callback, where the recalculation's {@code @Transactional} joins the finished transaction and nothing is
 * written (see {@code ProofTaxRecalcIT}). So a rejected job is logged and offered to the queue again,
 * waiting up to {@link #REJECTION_WAIT_SECONDS}; only if the queue is still full then is it dropped, with
 * an error in the log. Nothing is thrown: the caller is an after-commit callback, and an exception there
 * would reach the employee whose submit has already committed.
 */
@Configuration
@EnableAsync
public class TaxRecalcConfig {

    static final long REJECTION_WAIT_SECONDS = 30;

    @Bean("taxRecalc")
    public Executor taxRecalcExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(2);
        ex.setMaxPoolSize(4);
        ex.setQueueCapacity(100);
        ex.setThreadNamePrefix("tax-recalc-");
        ex.setRejectedExecutionHandler(new RetryingRejection(REJECTION_WAIT_SECONDS, TimeUnit.SECONDS));
        ex.initialize();
        return ex;
    }

    /** Logs a rejected job and waits for room in the queue rather than dropping it. */
    static final class RetryingRejection implements RejectedExecutionHandler {

        private static final Logger log = LoggerFactory.getLogger(RetryingRejection.class);

        private final long wait;
        private final TimeUnit unit;

        RetryingRejection(long wait, TimeUnit unit) {
            this.wait = wait;
            this.unit = unit;
        }

        @Override
        public void rejectedExecution(Runnable job, ThreadPoolExecutor executor) {
            if (executor.isShutdown()) {
                log.error("tax recalculation dropped: the taxRecalc pool is shut down");
                return;
            }
            log.warn(
                    "taxRecalc pool full (active={}, queued={}); waiting up to {} {} for room",
                    executor.getActiveCount(),
                    executor.getQueue().size(),
                    wait,
                    unit);
            boolean queued;
            try {
                queued = executor.getQueue().offer(job, wait, unit);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                queued = false;
            }
            if (!queued) {
                log.error("tax recalculation dropped: the taxRecalc queue stayed full for {} {}", wait, unit);
            }
        }
    }
}
