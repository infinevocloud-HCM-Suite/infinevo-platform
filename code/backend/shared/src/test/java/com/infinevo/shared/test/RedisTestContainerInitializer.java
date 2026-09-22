package com.infinevo.shared.test;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Testcontainers initializer for Redis 7.
 *
 * <p>Spawns a single Redis container shared across integration tests when Docker is available.
 */
public class RedisTestContainerInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final int REDIS_PORT = 6379;

    @SuppressWarnings("resource")
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(REDIS_PORT);

    private static volatile boolean started = false;

    public static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Exception e) {
            return false;
        }
    }

    public static String getHost() {
        return REDIS.getHost();
    }

    public static int getPort() {
        return REDIS.getMappedPort(REDIS_PORT);
    }

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        if (!isDockerAvailable()) {
            return;
        }

        synchronized (RedisTestContainerInitializer.class) {
            if (!started) {
                REDIS.start();
                started = true;
                Runtime.getRuntime().addShutdownHook(new Thread(REDIS::stop));
            }
        }

        TestPropertyValues.of(
                        "spring.data.redis.host=" + REDIS.getHost(),
                        "spring.data.redis.port=" + REDIS.getMappedPort(REDIS_PORT),
                        "spring.data.redis.timeout=2000ms",
                        "infinevo.cache.enabled=true")
                .applyTo(context.getEnvironment());
    }
}
