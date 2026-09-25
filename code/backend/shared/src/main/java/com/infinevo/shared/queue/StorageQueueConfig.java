package com.infinevo.shared.queue;

import com.azure.storage.queue.QueueMessageEncoding;
import com.azure.storage.queue.QueueServiceClient;
import com.azure.storage.queue.QueueServiceClientBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StorageQueueConfig {

    @Bean
    @ConditionalOnProperty(name = "azure.storage.queue.connection-string")
    public QueueServiceClient queueServiceClient(
            @Value("${azure.storage.queue.connection-string}") String connectionString) {
        return new QueueServiceClientBuilder()
                .connectionString(connectionString)
                .messageEncoding(QueueMessageEncoding.BASE64)
                .buildClient();
    }

    @Bean
    @ConditionalOnProperty(name = "azure.storage.queue.connection-string")
    public QueueProducer queueProducer(QueueServiceClient queueServiceClient, ObjectMapper objectMapper) {
        return new AzureStorageQueueProducer(queueServiceClient, objectMapper);
    }
}
