package com.infinevo.shared.test;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * W-04 — JUnit 5 {@link ExecutionCondition} that disables a test when Docker
 * is not available.
 *
 * <p>Evaluates <em>before</em> Spring attempts to load the application context,
 * preventing {@link org.testcontainers.containers.PostgreSQLContainer} startup
 * failures from surfacing as {@link ExceptionInInitializerError}.
 */
class DockerAvailableCondition implements ExecutionCondition {

    private static final ConditionEvaluationResult ENABLED = ConditionEvaluationResult.enabled("Docker is available");

    private static final ConditionEvaluationResult DISABLED =
            ConditionEvaluationResult.disabled("Docker is not available — Testcontainers tests skipped");

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        return PostgresTestContainerInitializer.isDockerAvailable() ? ENABLED : DISABLED;
    }
}
