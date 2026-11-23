package com.infinevo.core.ai;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Calls Azure OpenAI's Chat Completions on the {@code /openai/v1} route (W-114.4, spec "The Azure OpenAI call").
 *
 * <p>{@code POST {endpoint}/openai/v1/chat/completions}, no {@code api-version}; the body's {@code model} is the
 * deployment name. Signed in with a bearer token from the app's managed identity ({@link CachedToken}). The platform's
 * dotted tool names are sent with the dot as {@code __} - OpenAI function names allow only letters, digits, {@code _}
 * and {@code -} - and turned back on the way in; {@link AiToolRegistry} refuses at startup a name that already holds
 * {@code __}, so the way back is exact.
 *
 * <p>A content-filter stop, on the question ({@code 400}, code {@code content_filter}) or the answer
 * ({@code finish_reason: content_filter}), is {@link AiModelResponse.Kind#FILTERED}, not an error. Any other
 * {@code 4xx} or {@code 5xx}, a timeout, or a reply that cannot be read is {@link AiModelException}. Nothing sent or
 * received is logged.
 */
public class AzureOpenAiModelClient implements AiModelClient {

    static final String PATH = "/openai/v1/chat/completions";

    /** The answer-limit field. Older models read {@code max_tokens}; the client test pins this name. */
    static final String MAX_TOKENS_FIELD = "max_completion_tokens";

    private final URI url;
    private final String deployment;
    private final Supplier<String> tokens;
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();

    /**
     * @param endpoint {@code https://<resource>.openai.azure.com}; a trailing slash is dropped
     * @param deployment the deployment name, sent as {@code model}
     * @param tokens a bearer token for each call; {@link CachedToken} in Azure, a constant in a test
     */
    public AzureOpenAiModelClient(String endpoint, String deployment, Supplier<String> tokens, HttpClient http) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("endpoint must be set");
        }
        if (deployment == null || deployment.isBlank()) {
            throw new IllegalArgumentException("deployment must be set");
        }
        String base = endpoint.strip();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        this.url = URI.create(base + PATH);
        this.deployment = deployment.strip();
        this.tokens = Objects.requireNonNull(tokens, "tokens must not be null");
        this.http = Objects.requireNonNull(http, "http must not be null");
    }

    URI url() {
        return url;
    }

    @Override
    public AiModelResponse send(AiModelRequest request) {
        String body;
        try {
            body = json.writeValueAsString(body(request));
        } catch (JsonProcessingException e) {
            throw new AiModelException("The model request could not be written", e);
        }
        String token;
        try {
            token = tokens.get();
        } catch (RuntimeException e) {
            throw new AiModelException("No token for the model: " + e.getClass().getSimpleName(), e);
        }
        HttpRequest httpRequest = HttpRequest.newBuilder(url)
                .timeout(request.timeout())
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response;
        try {
            response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException e) {
            throw new AiModelException(
                    "The model did not answer within " + request.timeout().toSeconds() + " s", e);
        } catch (IOException e) {
            throw new AiModelException(
                    "The model could not be reached: " + e.getClass().getSimpleName(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AiModelException("Interrupted while waiting for the model", e);
        }
        return read(response.statusCode(), response.body());
    }

    ObjectNode body(AiModelRequest request) {
        ObjectNode root = json.createObjectNode();
        root.put("model", deployment);
        ArrayNode messages = root.putArray("messages");
        messages.addObject().put("role", "system").put("content", request.systemPrompt());
        for (AiModelRequest.Message m : request.messages()) {
            ObjectNode out = messages.addObject();
            switch (m.role()) {
                case USER -> out.put("role", "user").put("content", m.content());
                case ASSISTANT -> {
                    out.put("role", "assistant");
                    if (m.toolCalls().isEmpty()) {
                        out.put("content", m.content());
                    } else {
                        out.putNull("content");
                        ArrayNode calls = out.putArray("tool_calls");
                        for (AiModelResponse.ToolCall call : m.toolCalls()) {
                            ObjectNode c = calls.addObject();
                            c.put("id", call.id());
                            c.put("type", "function");
                            c.putObject("function")
                                    .put("name", toWire(call.name()))
                                    .put("arguments", call.argumentsJson() == null ? "{}" : call.argumentsJson());
                        }
                    }
                }
                case TOOL ->
                    out.put("role", "tool").put("tool_call_id", m.toolCallId()).put("content", m.content());
            }
        }
        if (!request.tools().isEmpty()) {
            // tool_choice without tools is refused by the API, so both go only when there are tools.
            ArrayNode tools = root.putArray("tools");
            for (AiToolDescriptor tool : request.tools()) {
                ObjectNode function = tools.addObject().put("type", "function").putObject("function");
                function.put("name", toWire(tool.name()));
                function.put("description", tool.description());
                if (tool.inputSchema() != null) {
                    function.set("parameters", tool.inputSchema());
                } else {
                    function.putObject("parameters").put("type", "object");
                }
            }
            root.put("tool_choice", "auto");
        }
        root.put(MAX_TOKENS_FIELD, request.maxOutputTokens());
        return root;
    }

    AiModelResponse read(int status, String body) {
        JsonNode root = parse(body);
        if (status == 400 && isContentFilter(root)) {
            return AiModelResponse.filtered();
        }
        if (status < 200 || status > 299) {
            throw new AiModelException("The model answered HTTP " + status);
        }
        if (root == null) {
            throw new AiModelException("The model's answer was not JSON");
        }
        int promptTokens = root.path("usage").path("prompt_tokens").asInt(0);
        int completionTokens = root.path("usage").path("completion_tokens").asInt(0);
        JsonNode choice = root.path("choices").path(0);
        if (choice.isMissingNode()) {
            throw new AiModelException("The model's answer held no choice");
        }
        String finish = choice.path("finish_reason").asText("");
        JsonNode message = choice.path("message");
        if ("content_filter".equals(finish)) {
            return AiModelResponse.filtered(promptTokens, completionTokens);
        }
        JsonNode calls = message.path("tool_calls");
        if (calls.isArray() && !calls.isEmpty()) {
            List<AiModelResponse.ToolCall> out = new ArrayList<>();
            for (JsonNode call : calls) {
                out.add(new AiModelResponse.ToolCall(
                        call.path("id").asText(""),
                        fromWire(call.path("function").path("name").asText("")),
                        call.path("function").path("arguments").asText("")));
            }
            return AiModelResponse.toolCalls(out, promptTokens, completionTokens);
        }
        JsonNode content = message.path("content");
        String text = content.isTextual() ? content.textValue() : "";
        if ("length".equals(finish)) {
            text = text.isBlank() ? AiModelResponse.CUT_SHORT : text.stripTrailing() + AiModelResponse.CUT_SHORT;
        } else if (text.isBlank()) {
            throw new AiModelException("The model's answer held no text and no tool call");
        }
        return AiModelResponse.text(text, promptTokens, completionTokens);
    }

    private JsonNode parse(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return json.readTree(body);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    /** Azure's prompt-filter refusal: {@code error.code} {@code content_filter}, or its inner policy code. */
    private static boolean isContentFilter(JsonNode root) {
        if (root == null) {
            return false;
        }
        JsonNode error = root.path("error");
        return "content_filter".equals(error.path("code").asText(null))
                || "ResponsibleAIPolicyViolation"
                        .equals(error.path("innererror").path("code").asText(null));
    }

    /** {@code core.my_leave_balances} → {@code core__my_leave_balances}. */
    static String toWire(String name) {
        return name == null ? "" : name.replace(".", "__");
    }

    /** {@code core__my_leave_balances} → {@code core.my_leave_balances}. */
    static String fromWire(String name) {
        return name == null ? "" : name.replace("__", ".");
    }

    /**
     * A managed-identity token, kept until five minutes before it expires (spec "Model access").
     *
     * <p>Thread-safe; at most one fetch at a time. A failed fetch throws, and the next call tries again.
     */
    public static final class CachedToken implements Supplier<String> {

        static final Duration REFRESH_BEFORE_EXPIRY = Duration.ofMinutes(5);
        private static final Duration FETCH_TIMEOUT = Duration.ofSeconds(20);

        private final TokenCredential credential;
        private final TokenRequestContext context;
        private final Clock clock;
        private AccessToken cached;

        public CachedToken(TokenCredential credential, String scope) {
            this(credential, scope, Clock.systemUTC());
        }

        CachedToken(TokenCredential credential, String scope, Clock clock) {
            this.credential = Objects.requireNonNull(credential, "credential must not be null");
            if (scope == null || scope.isBlank()) {
                throw new IllegalArgumentException("scope must be set");
            }
            this.context = new TokenRequestContext().addScopes(scope.strip());
            this.clock = Objects.requireNonNull(clock, "clock must not be null");
        }

        @Override
        public synchronized String get() {
            Instant now = clock.instant();
            if (cached == null
                    || cached.getExpiresAt() == null
                    || !now.isBefore(cached.getExpiresAt().toInstant().minus(REFRESH_BEFORE_EXPIRY))) {
                AccessToken fresh = credential.getToken(context).block(FETCH_TIMEOUT);
                if (fresh == null || fresh.getToken() == null) {
                    throw new IllegalStateException("The managed identity returned no token");
                }
                cached = fresh;
            }
            return cached.getToken();
        }
    }
}
