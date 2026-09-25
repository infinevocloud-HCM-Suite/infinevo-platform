package com.infinevo.worker.queue;

import com.azure.core.util.Context;
import com.azure.storage.queue.QueueClient;
import com.azure.storage.queue.QueueServiceClient;
import com.azure.storage.queue.models.QueueMessageItem;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.infinevo.core.job.service.JobService;
import com.infinevo.shared.queue.QueueConsumer;
import com.infinevo.shared.queue.QueueMessage;
import com.infinevo.shared.tenant.TenantContext;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "azure.storage.queue.connection-string")
public class QueueConsumerLoop implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(QueueConsumerLoop.class);

    private final QueueServiceClient queueServiceClient;
    private final ObjectMapper objectMapper;
    private final JobService jobService;
    private final Duration pollInterval;
    private final Duration visibilityTimeout;
    private final Map<String, QueueConsumer<String>> consumerMap = new HashMap<>();

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final List<Thread> workerThreads = new CopyOnWriteArrayList<>();

    public QueueConsumerLoop(
            QueueServiceClient queueServiceClient,
            ObjectMapper objectMapper,
            List<QueueConsumer<String>> consumers,
            JobService jobService,
            @Value("${azure.storage.queue.poll-interval:PT1S}") Duration pollInterval,
            @Value("${azure.storage.queue.visibility-timeout:PT5M}") Duration visibilityTimeout) {
        this.queueServiceClient = Objects.requireNonNull(queueServiceClient, "queueServiceClient must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.jobService = Objects.requireNonNull(jobService, "jobService must not be null");
        this.pollInterval = pollInterval != null ? pollInterval : Duration.ofSeconds(1);
        this.visibilityTimeout = visibilityTimeout != null ? visibilityTimeout : Duration.ofMinutes(5);

        if (consumers != null) {
            for (QueueConsumer<String> consumer : consumers) {
                String queueName = consumer.getQueueName();
                if (consumerMap.containsKey(queueName)) {
                    throw new IllegalStateException("Duplicate queue consumer for queue: " + queueName);
                }
                consumerMap.put(queueName, consumer);
            }
        }
    }

    @Override
    public synchronized void start() {
        if (running.compareAndSet(false, true)) {
            log.info("Starting QueueConsumerLoop for {} registered queue consumers", consumerMap.size());
            for (Map.Entry<String, QueueConsumer<String>> entry : consumerMap.entrySet()) {
                String queueName = entry.getKey();
                QueueConsumer<String> consumer = entry.getValue();

                Thread thread = new Thread(() -> runLoopForQueue(queueName, consumer), "queue-loop-" + queueName);
                thread.setDaemon(true);
                workerThreads.add(thread);
                thread.start();
            }
        }
    }

    @Override
    public synchronized void stop() {
        if (running.compareAndSet(true, false)) {
            log.info("Stopping QueueConsumerLoop...");
            for (Thread thread : workerThreads) {
                thread.interrupt();
            }
            for (Thread thread : workerThreads) {
                try {
                    thread.join(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            workerThreads.clear();
            log.info("QueueConsumerLoop stopped.");
        }
    }

    @Override
    public void stop(Runnable callback) {
        stop();
        if (callback != null) {
            callback.run();
        }
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    private void runLoopForQueue(String queueName, QueueConsumer<String> consumer) {
        log.info("Started listener thread for queue: {}", queueName);
        QueueClient queueClient = queueServiceClient.getQueueClient(queueName);
        try {
            queueClient.createIfNotExists();
        } catch (Exception e) {
            log.warn(
                    "Could not create queue {} (may already exist or insufficient permissions): {}",
                    queueName,
                    e.getMessage());
        }

        while (running.get()) {
            try {
                var iterable = queueClient.receiveMessages(32, visibilityTimeout, null, Context.NONE);
                List<QueueMessageItem> messages =
                        iterable != null ? iterable.stream().toList() : List.of();

                if (messages.isEmpty()) {
                    Thread.sleep(pollInterval.toMillis());
                    continue;
                }

                for (QueueMessageItem message : messages) {
                    if (!running.get()) {
                        break;
                    }
                    processSingleMessage(queueClient, queueName, consumer, message);
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                if (running.get()) {
                    log.error("Error receiving messages from queue {}: {}", queueName, e.getMessage(), e);
                    try {
                        Thread.sleep(pollInterval.toMillis());
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        log.info("Exited listener thread for queue: {}", queueName);
    }

    private void processSingleMessage(
            QueueClient queueClient, String queueName, QueueConsumer<String> consumer, QueueMessageItem message) {

        long dequeueCount = message.getDequeueCount();
        String rawBody = message.getMessageText() != null ? message.getMessageText() : "";

        if (dequeueCount >= 3) {
            log.error(
                    "Poison message detected on queue {} (dequeueCount={}). Deleting message.",
                    queueName,
                    dequeueCount);
            markFailedFromRawJson(rawBody, "poison: delivered " + dequeueCount + " times");
            deleteQuietly(queueClient, queueName, message, "poison");
            return;
        }

        QueueMessage<String> queueMsg;
        try {
            queueMsg = objectMapper.readValue(rawBody, new TypeReference<QueueMessage<String>>() {});
        } catch (Exception parseException) {
            log.error("Failed to parse queue message on queue {}: {}", queueName, parseException.getMessage());
            markFailedFromRawJson(rawBody, "unparseable: " + parseException.getMessage());
            deleteQuietly(queueClient, queueName, message, "unparseable");
            return;
        }

        try {
            consumer.onMessage(queueMsg);
            queueClient.deleteMessage(message.getMessageId(), message.getPopReceipt());
        } catch (Exception processException) {
            log.error(
                    "Error executing consumer for queue {}: {}",
                    queueName,
                    processException.getMessage(),
                    processException);
            // Leave message on queue; it reappears after the visibility timeout
        }
    }

    /**
     * Marks the job FAILED under the tenant named in the message. {@code core.job_status} is
     * RLS-isolated, so a {@code markFailed} with no tenant bound sees no row and writes nothing;
     * the job would sit QUEUED forever with no error. Nothing here throws: a failure to record
     * the failure is logged and the batch carries on.
     */
    private void markFailedFromRawJson(String rawJson, String reason) {
        String jobId = readField(rawJson, "jobId");
        String tenantText = readField(rawJson, "tenantId");
        if (jobId == null || jobId.isBlank()) {
            log.error("Cannot mark job FAILED ({}): no jobId in message body", reason);
            return;
        }
        UUID tenantId;
        try {
            tenantId = UUID.fromString(Objects.requireNonNull(tenantText, "tenantId missing"));
        } catch (Exception e) {
            log.error("Cannot mark job {} FAILED ({}): tenantId unusable: {}", jobId, reason, e.getMessage());
            return;
        }
        boolean wasBound = TenantContext.isBound();
        try {
            TenantContext.set(tenantId);
            jobService.markFailed(jobId, reason);
        } catch (Exception e) {
            log.error("Failed to mark job {} FAILED ({}): {}", jobId, reason, e.getMessage(), e);
        } finally {
            if (!wasBound) {
                TenantContext.clear();
            }
        }
    }

    private void deleteQuietly(QueueClient queueClient, String queueName, QueueMessageItem message, String why) {
        try {
            queueClient.deleteMessage(message.getMessageId(), message.getPopReceipt());
        } catch (Exception e) {
            log.error("Failed to delete {} message from queue {}: {}", why, queueName, e.getMessage(), e);
        }
    }

    private String readField(String rawJson, String field) {
        if (rawJson == null || rawJson.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(rawJson);
            if (node.hasNonNull(field)) {
                return node.get(field).asText();
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
