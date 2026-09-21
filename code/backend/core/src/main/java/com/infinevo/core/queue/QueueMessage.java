package com.infinevo.core.queue;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Standard immutable envelope for background job queue messages.
 * Enforces size limits (< 48 KB) for Azure Storage Queue compatibility (D-50).
 */
public final class QueueMessage<T> implements Serializable {

    public static final int MAX_PAYLOAD_BYTES = 48 * 1024; // 48 KB

    private final String messageId;
    private final String jobId;
    private final UUID tenantId;
    private final String queueName;
    private final Instant enqueuedAt;
    private final int retryCount;
    private final T payload;

    @JsonCreator
    public QueueMessage(
            @JsonProperty("messageId") String messageId,
            @JsonProperty("jobId") String jobId,
            @JsonProperty("tenantId") UUID tenantId,
            @JsonProperty("queueName") String queueName,
            @JsonProperty("enqueuedAt") Instant enqueuedAt,
            @JsonProperty("retryCount") int retryCount,
            @JsonProperty("payload") T payload) {
        this.messageId = Objects.requireNonNull(messageId, "messageId must not be null");
        this.jobId = Objects.requireNonNull(jobId, "jobId must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.queueName = Objects.requireNonNull(queueName, "queueName must not be null");
        this.enqueuedAt = enqueuedAt != null ? enqueuedAt : Instant.now();
        this.retryCount = retryCount;
        this.payload = payload;

        validatePayloadSize(payload);
    }

    public static <T> QueueMessage<T> of(String jobId, UUID tenantId, String queueName, T payload) {
        return new QueueMessage<>(UUID.randomUUID().toString(), jobId, tenantId, queueName, Instant.now(), 0, payload);
    }

    private void validatePayloadSize(T payload) {
        if (payload instanceof String str) {
            byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
            if (bytes.length > MAX_PAYLOAD_BYTES) {
                throw new PayloadTooLargeException(
                        "Payload size " + bytes.length + " bytes exceeds limit of " + MAX_PAYLOAD_BYTES + " bytes");
            }
        } else if (payload instanceof byte[] bytes) {
            if (bytes.length > MAX_PAYLOAD_BYTES) {
                throw new PayloadTooLargeException(
                        "Payload size " + bytes.length + " bytes exceeds limit of " + MAX_PAYLOAD_BYTES + " bytes");
            }
        }
    }

    public String getMessageId() {
        return messageId;
    }

    public String getJobId() {
        return jobId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getQueueName() {
        return queueName;
    }

    public Instant getEnqueuedAt() {
        return enqueuedAt;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public T getPayload() {
        return payload;
    }

    public QueueMessage<T> withIncrementedRetry() {
        return new QueueMessage<>(
                this.messageId,
                this.jobId,
                this.tenantId,
                this.queueName,
                this.enqueuedAt,
                this.retryCount + 1,
                this.payload);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        QueueMessage<?> that = (QueueMessage<?>) o;
        return retryCount == that.retryCount
                && Objects.equals(messageId, that.messageId)
                && Objects.equals(jobId, that.jobId)
                && Objects.equals(tenantId, that.tenantId)
                && Objects.equals(queueName, that.queueName)
                && Objects.equals(enqueuedAt, that.enqueuedAt)
                && Objects.equals(payload, that.payload);
    }

    @Override
    public int hashCode() {
        return Objects.hash(messageId, jobId, tenantId, queueName, enqueuedAt, retryCount, payload);
    }

    @Override
    public String toString() {
        return "QueueMessage{" + "messageId='"
                + messageId + '\'' + ", jobId='"
                + jobId + '\'' + ", tenantId="
                + tenantId + ", queueName='"
                + queueName + '\'' + ", enqueuedAt="
                + enqueuedAt + ", retryCount="
                + retryCount + '}';
    }
}
