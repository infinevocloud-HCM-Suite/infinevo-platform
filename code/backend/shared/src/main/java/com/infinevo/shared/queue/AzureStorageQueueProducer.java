package com.infinevo.shared.queue;

import com.azure.storage.queue.QueueClient;
import com.azure.storage.queue.QueueMessageEncoding;
import com.azure.storage.queue.QueueServiceClient;
import com.azure.storage.queue.QueueServiceClientBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Azure Storage Queue producer implementation (D-50).
 * Dispatches JSON-serialized QueueMessage envelopes to Azure Storage Queues (or Azurite locally).
 */
public class AzureStorageQueueProducer implements QueueProducer {

    private static final Logger log = LoggerFactory.getLogger(AzureStorageQueueProducer.class);

    private final QueueServiceClient queueServiceClient;
    private final ObjectMapper objectMapper;
    private final ConcurrentMap<String, QueueClient> queueClients = new ConcurrentHashMap<>();

    public AzureStorageQueueProducer(QueueServiceClient queueServiceClient, ObjectMapper objectMapper) {
        this.queueServiceClient = Objects.requireNonNull(queueServiceClient, "queueServiceClient must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    public AzureStorageQueueProducer(String connectionString, ObjectMapper objectMapper) {
        this(
                new QueueServiceClientBuilder()
                        .connectionString(Objects.requireNonNull(connectionString, "connectionString must not be null"))
                        .messageEncoding(QueueMessageEncoding.BASE64)
                        .buildClient(),
                objectMapper);
    }

    @Override
    public <T> void send(String queueName, QueueMessage<T> message) {
        Objects.requireNonNull(queueName, "queueName must not be null");
        Objects.requireNonNull(message, "message must not be null");

        try {
            QueueClient client = getOrCreateQueueClient(queueName);
            String json = objectMapper.writeValueAsString(message);
            client.sendMessage(json);
            log.debug("Sent message {} to queue {}", message.getMessageId(), queueName);
        } catch (PayloadTooLargeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to send message {} to queue {}: {}", message.getMessageId(), queueName, e.getMessage());
            throw new RuntimeException("Failed to enqueue message to " + queueName, e);
        }
    }

    public QueueClient getOrCreateQueueClient(String queueName) {
        return queueClients.computeIfAbsent(queueName, name -> {
            QueueClient client = queueServiceClient.getQueueClient(name);
            try {
                client.createIfNotExists();
            } catch (Exception e) {
                log.warn("Could not ensure queue {} exists: {}", name, e.getMessage());
            }
            return client;
        });
    }
}
