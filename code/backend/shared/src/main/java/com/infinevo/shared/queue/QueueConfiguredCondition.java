package com.infinevo.shared.queue;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Matches when Azure Storage Queue is configured (W-52.2).
 *
 * <p>Returns {@code true} when either {@code azure.storage.queue.connection-string} or
 * {@code azure.storage.queue.endpoint} is present and non-blank. Used instead of
 * {@code @ConditionalOnProperty} because that annotation matches empty strings, and the
 * application YAML defaults both keys to empty.
 */
public class QueueConfiguredCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String connStr = context.getEnvironment().getProperty("azure.storage.queue.connection-string");
        if (connStr != null && !connStr.isBlank()) {
            return true;
        }
        String endpoint = context.getEnvironment().getProperty("azure.storage.queue.endpoint");
        return endpoint != null && !endpoint.isBlank();
    }
}
