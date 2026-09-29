package com.infinevo.worker.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.infinevo.shared.queue.QueueMessage;
import com.infinevo.shared.queue.QueueProducer;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.worker.InfinevoWorkerApplication;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

/**
 * W-20.2 §7 — Integration test for notification delivery end-to-end:
 * <ul>
 *   <li>A queued notification is delivered once and its status updated to {@code SENT}</li>
 *   <li>A redelivered queue message (Storage Queue at-least-once guarantee, D-50) does not send twice</li>
 * </ul>
 */
@SpringBootTest(
        classes = InfinevoWorkerApplication.class,
        properties = {
            "azure.storage.queue.poll-interval=PT0.1S",
            "azure.storage.queue.visibility-timeout=PT2S",
            "BREVO_API_KEY=test-api-key",
            "infinevo.cache.enabled=false"
        })
@ContextConfiguration(
        initializers = {PostgresTestContainerInitializer.class, NotificationWorkerTestSchema.Initializer.class})
class DeliveryIT extends AbstractIntegrationTest {

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

    @MockBean
    private DeliveryClient deliveryClient;

    @Autowired
    private QueueProducer queueProducer;

    private UUID tenantId;
    private UUID employeeId;

    @BeforeAll
    static void initSchema() {
        NotificationWorkerTestSchema.jdbcUrl();
    }

    @BeforeEach
    void seed() throws SQLException {
        TenantContext.clear();
        tenantId = NotificationWorkerTestSchema.insertTenant("Delivery Tenant " + UUID.randomUUID(), "UTC");
        employeeId = NotificationWorkerTestSchema.insertEmployee(
                tenantId, "EMP-" + UUID.randomUUID(), "delivered@infinevo.test");
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Queued notification is delivered once and updated to SENT; redelivered message does not send twice")
    void queuedNotificationDeliveredOnceAndRedeliveryDoesNotSendTwice() throws Exception {
        UUID notificationId = NotificationWorkerTestSchema.insertNotification(
                tenantId,
                employeeId,
                "delivered@infinevo.test",
                "TIMESHEET_REMINDER",
                "EMAIL",
                "Timesheet Reminder",
                "Please submit your weekly timesheet.",
                "QUEUED");

        QueueMessage<String> message = QueueMessage.of(
                notificationId.toString(),
                tenantId,
                NotificationDeliveryConsumer.QUEUE_NAME,
                notificationId.toString());

        // Step 1: Send initial message to queue 'notification'
        queueProducer.send(NotificationDeliveryConsumer.QUEUE_NAME, message);

        // Step 2: Await delivery execution and status transition to SENT. Read as the owner: the
        // worker's own login sees nothing here with no tenant bound, which is RLS working.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            verify(deliveryClient, times(1))
                    .sendEmail("delivered@infinevo.test", "Timesheet Reminder", "Please submit your weekly timesheet.");

            try (Connection conn = NotificationWorkerTestSchema.migrationConnection();
                    PreparedStatement ps =
                            conn.prepareStatement("SELECT status, sent_at FROM core.notification WHERE id = ?")) {
                ps.setObject(1, notificationId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("status")).isEqualTo("SENT");
                    Timestamp sentAt = rs.getTimestamp("sent_at");
                    assertThat(sentAt).isNotNull();
                }
            }
        });

        // Step 3: Simulate Storage Queue at-least-once redelivery (D-50)
        queueProducer.send(NotificationDeliveryConsumer.QUEUE_NAME, message);

        // Allow consumer loop time to receive and process redelivered message
        Thread.sleep(1500);

        // Step 4: Verify deliveryClient was NOT invoked a second time
        verify(deliveryClient, times(1)).sendEmail(any(), any(), any());

        // Verify status remains SENT
        try (Connection conn = NotificationWorkerTestSchema.migrationConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT status FROM core.notification WHERE id = ?")) {
            ps.setObject(1, notificationId);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("status")).isEqualTo("SENT");
            }
        }
    }
}
