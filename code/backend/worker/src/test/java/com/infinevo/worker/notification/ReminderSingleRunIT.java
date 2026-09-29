package com.infinevo.worker.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.worker.InfinevoWorkerApplication;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.test.context.ContextConfiguration;

/**
 * W-20.2 §7 — Proves that ReminderEvaluator is protected by cluster-wide ShedLock:
 * <ul>
 *   <li>The {@code evaluateReminders()} method is annotated with {@code @Scheduled} and {@code @SchedulerLock(name = "reminder_evaluator")}</li>
 *   <li>Two worker instances against one database: the reminder job body executes exactly once across replicas</li>
 * </ul>
 */
@SpringBootTest(classes = InfinevoWorkerApplication.class)
@ContextConfiguration(
        initializers = {PostgresTestContainerInitializer.class, NotificationWorkerTestSchema.Initializer.class})
class ReminderSingleRunIT extends AbstractIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @BeforeAll
    static void initSchema() {
        NotificationWorkerTestSchema.jdbcUrl();
    }

    @Test
    @DisplayName(
            "ReminderEvaluator.evaluateReminders() carries @Scheduled and @SchedulerLock(name = 'reminder_evaluator')")
    void evaluateRemindersIsAnnotatedWithSchedulerLock() throws NoSuchMethodException {
        Method method = ReminderEvaluator.class.getMethod("evaluateReminders");

        assertThat(method.isAnnotationPresent(Scheduled.class))
                .as("Job must be @Scheduled")
                .isTrue();

        SchedulerLock lock = method.getAnnotation(SchedulerLock.class);
        assertThat(lock).as("Job must be paired with @SchedulerLock (DEBT-021)").isNotNull();

        assertThat(lock.name()).as("Lock name must be 'reminder_evaluator'").isEqualTo("reminder_evaluator");

        assertThat(lock.lockAtMostFor())
                .as("lockAtMostFor must be set to bound crashed replica hold time")
                .isNotEmpty();
    }

    @Test
    @DisplayName("Two worker instances against one database: reminder job body executes exactly once")
    void concurrentWorkerReplicasRunReminderJobExactlyOnce() throws InterruptedException {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        LockProvider lockProvider = new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(jdbcTemplate)
                .withTableName("core.shedlock")
                .usingDbTime()
                .build());

        LockingTaskExecutor executor = new DefaultLockingTaskExecutor(lockProvider);

        Method method;
        try {
            method = ReminderEvaluator.class.getMethod("evaluateReminders");
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
        SchedulerLock lockAnnotation = method.getAnnotation(SchedulerLock.class);
        String lockName = lockAnnotation.name();

        AtomicInteger executionCount = new AtomicInteger(0);
        int threadCount = 2;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        LockConfiguration lockConfig =
                new LockConfiguration(Instant.now(), lockName, Duration.ofSeconds(30), Duration.ZERO);

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    executor.executeWithLock(
                            (Runnable) () -> {
                                executionCount.incrementAndGet();
                                try {
                                    Thread.sleep(500); // hold lock so second replica is denied
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                            },
                            lockConfig);
                } catch (Exception ignored) {
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        // Trigger both replica threads simultaneously
        startLatch.countDown();
        finishLatch.await();
        executorService.shutdown();

        // Exactly 1 replica must have run, the other replica denied by ShedLock
        assertEquals(1, executionCount.get(), "Reminder job body must execute exactly once across 2 worker replicas");
    }
}
