package com.infinevo.core.document;

import com.azure.core.util.BinaryData;
import com.azure.core.util.Context;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.models.BlobHttpHeaders;
import com.azure.storage.blob.models.BlobRequestConditions;
import com.azure.storage.blob.options.BlobParallelUploadOptions;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link BlobStorage} over the Azure SDK (W-21) — Azure Blob Storage, or Azurite locally.
 *
 * <p>Every container is private: the platform never issues a public or SAS URL, and the storage
 * account has no public network path at all ({@code infra/azure/modules/storage.bicep:27,34}).
 */
class AzureBlobStorage implements BlobStorage {

    /** {@code If-None-Match: *} — the write succeeds only when no blob exists at the path. */
    private static final String NO_EXISTING_BLOB = "*";

    private final BlobServiceClient serviceClient;
    private final Map<String, BlobContainerClient> containers = new ConcurrentHashMap<>();

    AzureBlobStorage(BlobServiceClient serviceClient) {
        this.serviceClient = Objects.requireNonNull(serviceClient, "serviceClient must not be null");
    }

    @Override
    public void upload(String container, String path, byte[] content, String contentType) {
        write(container, path, BinaryData.fromBytes(content), contentType);
    }

    /** {@link BinaryData#fromFile} streams from disk in blocks; the file is never read whole. */
    @Override
    public void upload(String container, String path, Path file, String contentType) {
        write(container, path, BinaryData.fromFile(file), contentType);
    }

    private void write(String container, String path, BinaryData data, String contentType) {
        BlobClient blob = container(container).getBlobClient(path);
        BlobParallelUploadOptions options = new BlobParallelUploadOptions(data)
                .setHeaders(new BlobHttpHeaders().setContentType(contentType))
                .setRequestConditions(new BlobRequestConditions().setIfNoneMatch(NO_EXISTING_BLOB));
        blob.uploadWithResponse(options, null, Context.NONE);
    }

    @Override
    public InputStream open(String container, String path) {
        return container(container).getBlobClient(path).openInputStream();
    }

    /**
     * The container client, created on first use if it does not exist.
     *
     * <p>In Azure the containers are declared in Bicep ({@code storage.bicep}, {@code documents} among
     * them) and this is a no-op. Azurite starts empty, so locally and in tests the first upload creates
     * it — private, since no access level is passed.
     */
    private BlobContainerClient container(String name) {
        return containers.computeIfAbsent(name, n -> {
            BlobContainerClient client = serviceClient.getBlobContainerClient(n);
            client.createIfNotExists();
            return client;
        });
    }
}
