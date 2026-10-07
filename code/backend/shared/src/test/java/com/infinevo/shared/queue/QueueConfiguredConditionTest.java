package com.infinevo.shared.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.mock.env.MockEnvironment;

class QueueConfiguredConditionTest {

    private QueueConfiguredCondition condition;
    private ConditionContext context;
    private AnnotatedTypeMetadata metadata;
    private MockEnvironment environment;

    @BeforeEach
    void setUp() {
        condition = new QueueConfiguredCondition();
        context = mock(ConditionContext.class);
        metadata = mock(AnnotatedTypeMetadata.class);
        environment = new MockEnvironment();
        when(context.getEnvironment()).thenReturn(environment);
    }

    @Test
    @DisplayName("Neither connection-string nor endpoint set -> false")
    void neitherSetReturnsFalse() {
        assertThat(condition.matches(context, metadata)).isFalse();
    }

    @Test
    @DisplayName("Both properties set to blank strings -> false")
    void bothBlankReturnsFalse() {
        environment.setProperty("azure.storage.queue.connection-string", "   ");
        environment.setProperty("azure.storage.queue.endpoint", "");

        assertThat(condition.matches(context, metadata)).isFalse();
    }

    @Test
    @DisplayName("Connection string only -> true")
    void connectionStringOnlyReturnsTrue() {
        environment.setProperty(
                "azure.storage.queue.connection-string", "DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;");

        assertThat(condition.matches(context, metadata)).isTrue();
    }

    @Test
    @DisplayName("Endpoint only -> true")
    void endpointOnlyReturnsTrue() {
        environment.setProperty("azure.storage.queue.endpoint", "https://stinfinevodev.queue.core.windows.net/");

        assertThat(condition.matches(context, metadata)).isTrue();
    }
}
