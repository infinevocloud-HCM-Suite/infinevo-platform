package com.infinevo.core.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.azure.storage.queue.QueueClient;
import com.azure.storage.queue.QueueMessageEncoding;
import com.azure.storage.queue.QueueServiceClientBuilder;
import com.azure.storage.queue.models.PeekedMessageItem;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.AzuriteTestContainer;
import com.infinevo.shared.test.PostgresTestContainerInitializer;
import com.infinevo.shared.test.RedisTestContainerInitializer;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * W-20.1 spec section 7 — an email notification produces exactly one message on the
 * {@code notification} queue, and only once its row is committed.
 */
@SpringBootTest(classes = NotificationTestApp.class)
@ContextConfiguration(
        initializers = {
            PostgresTestContainerInitializer.class,
            NotificationTestSchema.Initializer.class,
            RedisTestContainerInitializer.class
        })
class NotificationQueueIT extends AbstractIntegrationTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private TransactionTemplate transactions;

    private final ObjectMapper json = new ObjectMapper();

    private QueueClient queue;
    private UUID tenant;
    private UUID employee;

    @BeforeEach
    void seed() throws Exception {
        queue = new QueueServiceClientBuilder()
                .connectionString(AzuriteTestContainer.connectionString())
                .messageEncoding(QueueMessageEncoding.BASE64)
                .buildClient()
                .getQueueClient(NotificationService.QUEUE);
        queue.createIfNotExists();
        queue.clearMessages();
        tenant = NotificationTestSchema.insertTenant("Queue " + UUID.randomUUID());
        employee = NotificationTestSchema.insertEmployee(tenant, "Q-" + UUID.randomUUID());
        TenantContext.set(tenant);
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("One event to an employee: two rows, and exactly one queue message - the email's id")
    void oneMessagePerEmail() throws Exception {
        List<UUID> ids = transactions.execute(status -> notificationService.compose(
                NotificationEvent.LEAVE_APPROVED,
                employee,
                Map.of(
                        "employee_name", "Asha",
                        "leave_type", "Casual",
                        "from_date", "2026-10-01",
                        "to_date", "2026-10-02")));

        assertThat(ids).hasSize(2);
        List<PeekedMessageItem> messages =
                queue.peekMessages(32, null, null).stream().toList();
        assertThat(messages).hasSize(1);

        JsonNode envelope = json.readTree(messages.get(0).getBody().toString());
        UUID emailId = UUID.fromString(envelope.get("payload").asText());
        assertThat(ids).contains(emailId);
        assertThat(envelope.get("tenantId").asText()).isEqualTo(tenant.toString());
        assertThat(NotificationTestSchema.count(
                        "SELECT count(*) FROM core.notification WHERE id = ? AND channel = 'EMAIL'"
                                + " AND status = 'QUEUED'",
                        emailId))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("A rolled-back transaction leaves no row and no message")
    void rollbackQueuesNothing() throws Exception {
        transactions.execute(status -> {
            notificationService.compose(
                    NotificationEvent.TIMESHEET_REMINDER,
                    employee,
                    Map.of("employee_name", "Asha", "week_start", "2026-09-21"));
            status.setRollbackOnly();
            return null;
        });

        assertThat(queue.peekMessages(32, null, null).stream().toList()).isEmpty();
        assertThat(NotificationTestSchema.count(
                        "SELECT count(*) FROM core.notification WHERE recipient_employee_id = ?", employee))
                .isZero();
    }
}
