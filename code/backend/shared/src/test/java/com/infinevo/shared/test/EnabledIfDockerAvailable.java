package com.infinevo.shared.test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * W-04 — Disables the annotated test class when Docker is not available.
 *
 * <p>This annotation is applied to {@link AbstractIntegrationTest} so that all
 * integration tests that extend it are gracefully skipped on machines without
 * Docker (e.g., local dev without Docker Desktop). CI environments with Docker
 * will execute the tests normally.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(DockerAvailableCondition.class)
public @interface EnabledIfDockerAvailable {}
