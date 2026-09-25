package com.infinevo.worker.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.azure.storage.queue.QueueServiceClient;
import com.infinevo.core.job.JobState;
import com.infinevo.core.job.dto.JobStatusResponseDTO;
import com.infinevo.core.job.service.JobService;
import com.infinevo.shared.queue.QueueConsumer;
import com.infinevo.shared.queue.QueueMessage;
import com.infinevo.shared.queue.QueueProducer;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.worker.listener.PayrunQueueListener;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

/**
 * W-52.1 §7 — the one externally testable behaviour, end to end against a real queue.
 *
 * <p>Azurite's queue service stands in for Azure Storage Queue, Postgres holds {@code
 * core.job_status} under RLS, and the real {@link QueueConsumerLoop}, {@link PayrunQueueListener}
 * and {@link JobService} run. Nothing is mocked, so the tenant binding that {@code markFailed}
 * needs to reach an RLS-isolated row is exercised for real.
 */
@SpringBootTest(
        classes = QueueRoundTripIT.TestApp.class,
        properties = {
            "azure.storage.queue.poll-interval=PT0.2S",
            // Short on purpose: the retry test waits for three deliveries of one message.
            "azure.storage.queue.visibility-timeout=PT1S",
            "infinevo.cache.enabled=false"
        })
class QueueRoundTripIT extends AbstractIntegrationTest {

    static final String FAILING_QUEUE = "it-fail";

    @SpringBootApplication(
            scanBasePackages = {
                "com.infinevo.worker.queue",
                "com.infinevo.worker.listener",
                "com.infinevo.shared.queue",
                "com.infinevo.shared.tenant",
                "com.infinevo.core.job"
            },
            exclude = {FlywayAutoConfiguration.class})
    @EnableJpaRepositories(basePackages = "com.infinevo.core.job")
    @EntityScan(basePackages = "com.infinevo.core.job")
    static class TestApp {

        /** A second listener, on its own queue, whose payload step always throws. */
        @Bean
        QueueConsumer<String> alwaysFailingListener(JobService jobService, AtomicInteger attempts) {
            return new PayrunQueueListener(jobService) {
                @Override
                public String getQueueName() {
                    return FAILING_QUEUE;
                }

                @Override
                protected void processPayrunPayload(String payload) {
                    attempts.incrementAndGet();
                    throw new IllegalStateException("boom " + attempts.get());
                }
            };
        }

        @Bean
        AtomicInteger attempts() {
            return new AtomicInteger();
        }
    }

    private static final int AZURITE_QUEUE_PORT = 10001;
    private static final String AZURITE_ACCOUNT = "devstoreaccount1";
    private static final String AZURITE_KEY =
            "Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==";

    @Container
    static final GenericContainer<?> AZURITE = new GenericContainer<>(
                    DockerImageName.parse("mcr.microsoft.com/azure-storage/azurite:3.33.0"))
            .withCommand(
                    "azurite-queue",
                    "--queueHost",
                    "0.0.0.0",
                    "--queuePort",
                    String.valueOf(AZURITE_QUEUE_PORT),
                    "--skipApiVersionCheck")
            .withExposedPorts(AZURITE_QUEUE_PORT);

    @DynamicPropertySource
    static void azurite(DynamicPropertyRegistry registry) {
        registry.add(
                "azure.storage.queue.connection-string",
                () -> "DefaultEndpointsProtocol=http;AccountName="
                        + AZURITE_ACCOUNT + ";AccountKey=" + AZURITE_KEY + ";QueueEndpoint=http://" + AZURITE.getHost()
                        + ":"
                        + AZURITE.getMappedPort(AZURITE_QUEUE_PORT) + "/" + AZURITE_ACCOUNT + ";");
    }

    private static final UUID TENANT_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final Duration WAIT = Duration.ofSeconds(30);

    @Autowired
    private JobService jobService;

    @Autowired
    private QueueProducer producer;

    @Autowired
    private QueueServiceClient queueServiceClient;

    @Autowired
    private AtomicInteger attempts;

    private static Connection migrationUserConnection() throws SQLException {
        return DriverManager.getConnection(
                PostgresTestContainerInitializer.getJdbcUrl(),
                PostgresTestContainerInitializer.MIGRATION_USER,
                PostgresTestContainerInitializer.MIGRATION_USER_PASSWORD);
    }

    @BeforeAll
    static void applyMigrations() throws Exception {
        try (Connection conn = migrationUserConnection()) {
            if (!tableExists(conn, "tenant")) {
                executeSqlResource(conn, "db/migration/core/V001__tenant.sql");
            }
            if (!tableExists(conn, "job_status")) {
                executeSqlResource(conn, "db/migration/core/V006__job_status_and_shedlock.sql");
            }
        }
    }

    private static boolean tableExists(Connection conn, String table) throws SQLException {
        try (PreparedStatement ps =
                conn.prepareStatement("SELECT 1 FROM pg_tables WHERE schemaname = 'core' AND tablename = ?")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void executeSqlResource(Connection conn, String resourcePath) throws Exception {
        try (InputStream is = QueueRoundTripIT.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalStateException("Migration script not on the test classpath: " + resourcePath);
            }
            String sql = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(sql);
            }
        }
    }

    @BeforeEach
    void seedTenant() throws SQLException {
        TenantContext.clear();
        attempts.set(0);
        try (Connection conn = migrationUserConnection();
                Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO core.tenant (tenant_id, name) VALUES ('" + TENANT_A
                    + "', 'Tenant A') ON CONFLICT DO NOTHING");
        }
    }

    private Optional<JobStatusResponseDTO> statusOf(String jobId) {
        TenantContext.set(TENANT_A);
        try {
            return jobService.getJobStatus(jobId, TENANT_A);
        } finally {
            TenantContext.clear();
        }
    }

    private void createQueuedJob(String jobId, String queue) {
        TenantContext.set(TENANT_A);
        try {
            jobService.createJob(jobId, TENANT_A, queue, "{}");
        } finally {
            TenantContext.clear();
        }
    }

    private long messagesOn(String queue) {
        return queueServiceClient.getQueueClient(queue).getProperties().getApproximateMessagesCount();
    }

    @Test
    @DisplayName("A message on payrun is consumed once: the job reaches COMPLETED and a re-send is dropped")
    void roundTripCompletesOnceAndDropsTheDuplicate() {
        String jobId = "rt-" + UUID.randomUUID();
        createQueuedJob(jobId, PayrunQueueListener.QUEUE_NAME);
        QueueMessage<String> message =
                QueueMessage.of(jobId, TENANT_A, PayrunQueueListener.QUEUE_NAME, "{\"run\":\"monthly\"}");

        producer.send(PayrunQueueListener.QUEUE_NAME, message);

        await().atMost(WAIT).untilAsserted(() -> assertThat(statusOf(jobId))
                .get()
                .extracting(JobStatusResponseDTO::status)
                .isEqualTo(JobState.COMPLETED));
        JobStatusResponseDTO completed = statusOf(jobId).orElseThrow();
        assertThat(completed.progressPercentage()).isEqualTo(100);
        await().atMost(WAIT).until(() -> messagesOn(PayrunQueueListener.QUEUE_NAME) == 0);

        // Same message again: Storage Queue is at-least-once and the loop must be idempotent.
        producer.send(PayrunQueueListener.QUEUE_NAME, message);
        await().atMost(WAIT).until(() -> messagesOn(PayrunQueueListener.QUEUE_NAME) == 0);

        JobStatusResponseDTO after = statusOf(jobId).orElseThrow();
        assertThat(after.status()).isEqualTo(JobState.COMPLETED);
        assertThat(after.updatedAt())
                .as("a dropped duplicate must not touch the row")
                .isEqualTo(completed.updatedAt());
    }

    @Test
    @DisplayName("A job that keeps throwing is retried and marked FAILED on the third delivery, under its tenant")
    void failingJobIsRetriedThenMarkedFailed() {
        String jobId = "fail-" + UUID.randomUUID();
        createQueuedJob(jobId, FAILING_QUEUE);

        producer.send(FAILING_QUEUE, QueueMessage.of(jobId, TENANT_A, FAILING_QUEUE, "{}"));

        await().atMost(WAIT).untilAsserted(() -> assertThat(statusOf(jobId))
                .get()
                .extracting(JobStatusResponseDTO::status)
                .isEqualTo(JobState.FAILED));
        await().atMost(WAIT).until(() -> messagesOn(FAILING_QUEUE) == 0);

        JobStatusResponseDTO failed = statusOf(jobId).orElseThrow();
        assertThat(failed.errorMessage())
                .startsWith("poison: delivered 3 times")
                .contains("boom 2");
        assertThat(attempts.get())
                .as("two attempts ran; the third delivery is poison")
                .isEqualTo(2);
    }
}
