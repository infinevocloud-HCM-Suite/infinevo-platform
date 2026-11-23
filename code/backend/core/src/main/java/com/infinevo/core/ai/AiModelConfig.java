package com.infinevo.core.ai;

import com.azure.identity.ManagedIdentityCredentialBuilder;
import java.net.http.HttpClient;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Chooses the AI helper's model client (W-114.4, spec "Model access").
 *
 * <ul>
 *   <li><strong>{@code infinevo.ai.endpoint} blank</strong> - {@link StubAiModelClient}: every laptop and every CI
 *       run. The app starts and the helper says it is not connected.
 *   <li><strong>An endpoint</strong> - {@link AzureOpenAiModelClient}, signed in as the container app's managed
 *       identity, the way the document store is ({@code core/document/DocumentStorageConfig.java:47}). The deployment
 *       must then be set too, or startup fails: a half-configured model is a deploy mistake to see at once.
 * </ul>
 *
 * <p>No {@code api-version} setting: the {@code /openai/v1} route needs none. The token scope is a setting because
 * which one the route accepts is not confirmed; a {@code 401} at first deploy is fixed by changing it.
 */
@Configuration(proxyBeanMethods = false)
class AiModelConfig {

    private static final Logger log = LoggerFactory.getLogger(AiModelConfig.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    @Bean
    AiModelClient aiModelClient(
            @Value("${infinevo.ai.endpoint:}") String endpoint,
            @Value("${infinevo.ai.deployment:}") String deployment,
            @Value("${infinevo.ai.managed-identity-client-id:}") String clientId,
            @Value("${infinevo.ai.token-scope:https://cognitiveservices.azure.com/.default}") String tokenScope) {
        if (isBlank(endpoint)) {
            log.info("AI helper: stub model client");
            return new StubAiModelClient();
        }
        if (isBlank(deployment)) {
            throw new IllegalStateException("infinevo.ai.endpoint is set but infinevo.ai.deployment is not");
        }
        ManagedIdentityCredentialBuilder credential = new ManagedIdentityCredentialBuilder();
        if (!isBlank(clientId)) {
            credential.clientId(clientId.trim());
        }
        String scope = isBlank(tokenScope) ? "https://cognitiveservices.azure.com/.default" : tokenScope.trim();
        log.info("AI helper: Azure OpenAI at {}, deployment {}, token scope {}", endpoint.trim(), deployment, scope);
        return new AzureOpenAiModelClient(
                endpoint,
                deployment,
                new AzureOpenAiModelClient.CachedToken(credential.build(), scope),
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
