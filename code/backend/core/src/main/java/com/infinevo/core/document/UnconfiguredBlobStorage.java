package com.infinevo.core.document;

import java.io.InputStream;
import java.nio.file.Path;

/**
 * The {@link BlobStorage} a runtime gets when neither a connection string nor an endpoint is
 * configured (W-21).
 *
 * <p>Refuses every call with {@link DocumentService.StorageUnavailableException}, answered
 * {@code 503}. The alternative, failing at startup, would stop {@code app} and {@code worker} from
 * starting in any environment where the store is not wired yet — a local run outside compose, or a
 * deployment made before the storage account exists. A store that says plainly it is unavailable is
 * safer than an application that will not start for a feature most requests never touch.
 */
class UnconfiguredBlobStorage implements BlobStorage {

    static final String MESSAGE = "Document storage is not configured in this environment";

    @Override
    public void upload(String container, String path, byte[] content, String contentType) {
        throw new DocumentService.StorageUnavailableException(MESSAGE);
    }

    @Override
    public void upload(String container, String path, Path file, String contentType) {
        throw new DocumentService.StorageUnavailableException(MESSAGE);
    }

    @Override
    public InputStream open(String container, String path) {
        throw new DocumentService.StorageUnavailableException(MESSAGE);
    }
}
