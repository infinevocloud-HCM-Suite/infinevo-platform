package com.infinevo.shared.test;

import java.security.SecureRandom;
import java.util.Base64;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * One Azurite container for the whole test JVM — Azure Blob and Queue Storage, locally (W-21).
 *
 * <p>Lives in the {@code shared} test-jar beside {@link PostgresTestContainerInitializer} and
 * {@link RedisTestContainerInitializer}, so every module that needs storage in a test shares one
 * emulator rather than each starting its own: {@code core}'s document store now, the queue round trip
 * of {@code W-52.1} and the notification queue of {@code W-20.1} next.
 *
 * <p><strong>The account key is generated per run</strong> and handed to Azurite through
 * {@code AZURITE_ACCOUNTS}. Azurite's built-in account uses a published constant key, and a
 * committed copy of it is exactly what the secret scanners in CI flag; a key that exists only in this
 * JVM's memory is not in the repository to flag.
 *
 * <p>Not an {@code ApplicationContextInitializer}: which property a connection string belongs in is
 * the consuming module's business ({@code document.blob.connection-string} in {@code core}), and
 * {@code shared} should not know it. A module's own initializer calls {@link #connectionString()}.
 */
public final class AzuriteTestContainer {

    /** Same image the local stack runs as {@code blob} ({@code infra/docker/compose.yml}). */
    private static final DockerImageName IMAGE = DockerImageName.parse("mcr.microsoft.com/azure-storage/azurite");

    private static final int BLOB_PORT = 10000;
    private static final int QUEUE_PORT = 10001;

    public static final String ACCOUNT = "infinevotest";

    private static final String KEY = randomKey();

    @SuppressWarnings("resource") // container lifecycle is managed by the JVM shutdown hook
    private static final GenericContainer<?> AZURITE = new GenericContainer<>(IMAGE)
            .withCommand(
                    "azurite", "--blobHost", "0.0.0.0", "--queueHost", "0.0.0.0", "--skipApiVersionCheck", "--loose")
            .withEnv("AZURITE_ACCOUNTS", ACCOUNT + ":" + KEY)
            .withExposedPorts(BLOB_PORT, QUEUE_PORT)
            .waitingFor(Wait.forListeningPorts(BLOB_PORT, QUEUE_PORT));

    private static volatile boolean started = false;

    private AzuriteTestContainer() {}

    public static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * A connection string for the test account, blob and queue endpoints both, starting the container
     * on first use.
     */
    public static String connectionString() {
        startIfNeeded();
        String host = AZURITE.getHost();
        return "DefaultEndpointsProtocol=http;AccountName=" + ACCOUNT + ";AccountKey=" + KEY
                + ";BlobEndpoint=http://" + host + ":" + AZURITE.getMappedPort(BLOB_PORT) + "/" + ACCOUNT
                + ";QueueEndpoint=http://" + host + ":" + AZURITE.getMappedPort(QUEUE_PORT) + "/" + ACCOUNT + ";";
    }

    private static synchronized void startIfNeeded() {
        if (!started) {
            AZURITE.start();
            started = true;
            Runtime.getRuntime().addShutdownHook(new Thread(AZURITE::stop));
        }
    }

    /** 64 random bytes, base64 — the shape Azurite requires of an account key. */
    private static String randomKey() {
        byte[] key = new byte[64];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }
}
