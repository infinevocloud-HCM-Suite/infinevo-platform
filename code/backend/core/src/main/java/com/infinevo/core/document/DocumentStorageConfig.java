package com.infinevo.core.document;

import com.azure.identity.ManagedIdentityCredentialBuilder;
import com.azure.storage.blob.BlobServiceClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Chooses how this runtime reaches Blob Storage (W-21, decision D5).
 *
 * <ul>
 *   <li><strong>A connection string</strong> — Azurite, locally and in the integration tests. Only
 *       an emulator is ever given one.
 *   <li><strong>An endpoint and a managed identity</strong> — Azure. W-51 forbids account keys and
 *       SAS there ({@code infra/azure/modules/rbac.bicep:18-20}), and each container app already
 *       holds Storage Blob Data Contributor on its own user-assigned identity. {@code AZURE_CLIENT_ID}
 *       names that identity: with more than one assignable, the SDK will not guess.
 *   <li><strong>Neither</strong> — {@link UnconfiguredBlobStorage}, answering {@code 503}. The
 *       application still starts; the store says plainly that it is not there.
 * </ul>
 *
 * <p>This replaces the frozen system's approach outright: both products hold a Cloudinary API key
 * and secret in committed properties files ({@code legacy/.../application.properties}, DEBT-004).
 * Here no credential for storage exists anywhere to commit. The connection string, which does carry
 * a key, is never logged.
 */
@Configuration(proxyBeanMethods = false)
class DocumentStorageConfig {

    private static final Logger log = LoggerFactory.getLogger(DocumentStorageConfig.class);

    @Bean
    BlobStorage documentBlobStorage(
            @Value("${document.blob.connection-string:}") String connectionString,
            @Value("${document.blob.endpoint:}") String endpoint,
            @Value("${document.blob.managed-identity-client-id:}") String clientId) {
        if (!isBlank(connectionString)) {
            log.info("Document store: Blob by connection string (an emulator)");
            return new AzureBlobStorage(new BlobServiceClientBuilder()
                    .connectionString(connectionString)
                    .buildClient());
        }
        if (!isBlank(endpoint)) {
            ManagedIdentityCredentialBuilder credential = new ManagedIdentityCredentialBuilder();
            if (!isBlank(clientId)) {
                credential.clientId(clientId.trim());
            }
            log.info("Document store: Blob at {} by managed identity", endpoint);
            return new AzureBlobStorage(new BlobServiceClientBuilder()
                    .endpoint(endpoint.trim())
                    .credential(credential.build())
                    .buildClient());
        }
        log.warn("Neither document.blob.connection-string nor document.blob.endpoint is set; the document"
                + " store will answer 503");
        return new UnconfiguredBlobStorage();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
