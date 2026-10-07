package com.infinevo.shared.queue;

import com.azure.identity.ManagedIdentityCredentialBuilder;
import com.azure.storage.queue.QueueMessageEncoding;
import com.azure.storage.queue.QueueServiceClient;
import com.azure.storage.queue.QueueServiceClientBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;

/**
 * Chooses how this runtime reaches Storage Queue (W-52, W-52.1, W-52.2).
 *
 * <ul>
 *   <li><strong>A connection string</strong> — Azurite, locally and in the integration tests. Only
 *       an emulator is ever given one. Connection string wins when both are set.
 *   <li><strong>An endpoint and a managed identity</strong> — Azure. W-51 forbids account keys and
 *       SAS there ({@code infra/azure/modules/rbac.bicep:18-20}), and container apps hold Storage
 *       Queue Data Message Sender (app) and Storage Queue Data Message Processor (worker) on their
 *       own user-assigned identity ({@code rbac.bicep:113-125}). {@code AZURE_CLIENT_ID} names that
 *       identity: with more than one assignable, the SDK will not guess.
 *   <li><strong>Neither</strong> — no client or producer bean is created. Delivery falls back to
 *       the sweep ({@code W-20.2}), exactly as if the queue were unconfigured.
 * </ul>
 *
 * <p>The connection string, which does carry a key in Azurite, is never logged.
 */
@Configuration(proxyBeanMethods = false)
public class StorageQueueConfig {

    private static final Logger log = LoggerFactory.getLogger(StorageQueueConfig.class);

    @Bean
    @Conditional(QueueConfiguredCondition.class)
    public QueueServiceClient queueServiceClient(
            @Value("${azure.storage.queue.connection-string:}") String connectionString,
            @Value("${azure.storage.queue.endpoint:}") String endpoint,
            @Value("${azure.storage.queue.managed-identity-client-id:}") String clientId) {
        if (!isBlank(connectionString)) {
            log.info("Queue: Storage Queue by connection string (an emulator)");
            return new QueueServiceClientBuilder()
                    .connectionString(connectionString.trim())
                    .messageEncoding(QueueMessageEncoding.BASE64)
                    .buildClient();
        }
        if (!isBlank(endpoint)) {
            ManagedIdentityCredentialBuilder credential = new ManagedIdentityCredentialBuilder();
            if (!isBlank(clientId)) {
                credential.clientId(clientId.trim());
            }
            log.info("Queue: Storage Queue at {} by managed identity", endpoint.trim());
            return new QueueServiceClientBuilder()
                    .endpoint(endpoint.trim())
                    .credential(credential.build())
                    .messageEncoding(QueueMessageEncoding.BASE64)
                    .buildClient();
        }
        throw new IllegalStateException(
                "QueueConfiguredCondition matched but neither connection-string nor endpoint is configured");
    }

    @Bean
    @Conditional(QueueConfiguredCondition.class)
    public QueueProducer queueProducer(QueueServiceClient queueServiceClient, ObjectMapper objectMapper) {
        return new AzureStorageQueueProducer(queueServiceClient, objectMapper);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
