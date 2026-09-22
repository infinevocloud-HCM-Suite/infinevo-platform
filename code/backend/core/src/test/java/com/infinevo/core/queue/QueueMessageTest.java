package com.infinevo.core.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QueueMessageTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    void shouldCreateAndSerializeQueueMessage() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String jobId = UUID.randomUUID().toString();
        QueueMessage<String> message = QueueMessage.of(jobId, tenantId, "payrun", "{\"action\":\"START\"}");

        assertEquals(jobId, message.getJobId());
        assertEquals(tenantId, message.getTenantId());
        assertEquals("payrun", message.getQueueName());
        assertEquals("{\"action\":\"START\"}", message.getPayload());
        assertEquals(0, message.getRetryCount());
        assertNotNull(message.getEnqueuedAt());
        assertNotNull(message.getMessageId());

        String json = objectMapper.writeValueAsString(message);
        assertNotNull(json);

        QueueMessage<?> deserialized = objectMapper.readValue(json, QueueMessage.class);
        assertEquals(message.getMessageId(), deserialized.getMessageId());
        assertEquals(message.getJobId(), deserialized.getJobId());
        assertEquals(message.getTenantId(), deserialized.getTenantId());
        assertEquals(message.getQueueName(), deserialized.getQueueName());
        assertEquals(message.getPayload(), deserialized.getPayload());
    }

    @Test
    void shouldIncrementRetryCount() {
        UUID tenantId = UUID.randomUUID();
        QueueMessage<String> msg = QueueMessage.of("job-1", tenantId, "payrun", "payload");
        assertEquals(0, msg.getRetryCount());

        QueueMessage<String> retried = msg.withIncrementedRetry();
        assertEquals(1, retried.getRetryCount());
        assertEquals(msg.getMessageId(), retried.getMessageId());
        assertEquals(msg.getJobId(), retried.getJobId());
        assertEquals(msg.getTenantId(), retried.getTenantId());
    }

    @Test
    void shouldEnforceMaxPayloadSize() {
        UUID tenantId = UUID.randomUUID();
        byte[] oversizedBytes = new byte[49 * 1024]; // 49 KB > 48 KB limit
        String oversizedString = new String(oversizedBytes, StandardCharsets.UTF_8);

        PayloadTooLargeException ex = assertThrows(
                PayloadTooLargeException.class, () -> QueueMessage.of("job-1", tenantId, "payrun", oversizedString));
        assertTrue(ex.getMessage().contains("exceeds limit"));
    }

    @Test
    void shouldEnforceMaxPayloadSizeOnByteArray() {
        UUID tenantId = UUID.randomUUID();
        byte[] oversizedBytes = new byte[49 * 1024];

        PayloadTooLargeException ex = assertThrows(
                PayloadTooLargeException.class, () -> QueueMessage.of("job-1", tenantId, "payrun", oversizedBytes));
        assertTrue(ex.getMessage().contains("exceeds limit"));
    }

    @Test
    void shouldPropagateCorrelationIdThroughSerialization() throws Exception {
        UUID tenantId = UUID.randomUUID();
        QueueMessage<String> message =
                QueueMessage.of("job-123", tenantId, "payrun", "corr-test-999", "{\"action\":\"RUN\"}");

        assertEquals("corr-test-999", message.getCorrelationId());

        String json = objectMapper.writeValueAsString(message);
        QueueMessage<?> deserialized = objectMapper.readValue(json, QueueMessage.class);

        assertEquals("corr-test-999", deserialized.getCorrelationId());
    }

    @Test
    void shouldRejectNullMandatoryFields() {
        UUID tenantId = UUID.randomUUID();
        assertThrows(NullPointerException.class, () -> QueueMessage.of(null, tenantId, "payrun", "data"));
        assertThrows(NullPointerException.class, () -> QueueMessage.of("job-1", null, "payrun", "data"));
        assertThrows(NullPointerException.class, () -> QueueMessage.of("job-1", tenantId, null, "data"));
    }
}
