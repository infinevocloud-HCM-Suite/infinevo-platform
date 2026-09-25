package com.infinevo.core.document;

import java.io.InputStream;
import java.nio.file.Path;

/**
 * Where the bytes live (W-21). Azure Blob Storage in every environment, Azurite locally.
 *
 * <p>An interface so the service can be unit-tested with no storage at all, and so how Azure is
 * reached — a managed identity, since W-51 forbids account keys there
 * ({@code infra/azure/modules/rbac.bicep:18-20}) — is decided in {@link DocumentStorageConfig} alone
 * rather than in the service.
 */
public interface BlobStorage {

    /**
     * Writes a new blob from bytes already in memory — an upload, bounded at 10 MB.
     *
     * <p><strong>Never overwrites</strong>: a path that already exists is an error, because every path
     * names a fresh document id and a collision means something is wrong.
     *
     * @throws DocumentService.StorageUnavailableException when there is no storage to write to
     */
    void upload(String container, String path, byte[] content, String contentType);

    /**
     * Writes a new blob from a file, streamed rather than read into memory — a file the platform
     * generated, such as an export, which may be far larger than an upload (decision D2). Never
     * overwrites, as above.
     *
     * @throws DocumentService.StorageUnavailableException when there is no storage to write to
     */
    void upload(String container, String path, Path file, String contentType);

    /**
     * Opens an existing blob for reading. The caller closes the stream.
     *
     * @throws DocumentService.StorageUnavailableException when there is no storage to read from
     */
    InputStream open(String container, String path);
}
