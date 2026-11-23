package com.infinevo.core.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenCredential;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

/** W-114.4 spec, Tests: {@link AzureOpenAiModelClient} against a local HTTP server. No Azure. */
class AzureOpenAiModelClientTest {

    private final ObjectMapper json = new ObjectMapper();
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> query = new AtomicReference<>();
    private final AtomicReference<String> auth = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String reply = "{}";
    private volatile long delayMillis = 0;

    private HttpServer server;
    private AzureOpenAiModelClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            query.set(exchange.getRequestURI().getQuery());
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            if (delayMillis > 0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] bytes = reply.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            } catch (IOException ignored) {
                // the client gave up (timeout test)
            }
        });
        server.start();
        String endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
        client = new AzureOpenAiModelClient(endpoint, "gpt-helper", () -> "test-token", HttpClient.newHttpClient());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    @DisplayName("URL ends /openai/v1/chat/completions with no api-version; bearer token sent; trailing slash dropped")
    void urlAndAuth() {
        reply = textReply("Hello", "stop");
        client.send(request(List.of()));

        assertThat(path.get()).isEqualTo("/openai/v1/chat/completions");
        assertThat(query.get()).isNull();
        assertThat(auth.get()).isEqualTo("Bearer test-token");
        assertThat(client.url().toString()).doesNotContain("api-version").doesNotContain("//openai");
    }

    @Test
    @DisplayName("body: deployment as model, system first, tools as functions with __, tool_choice auto, 1024 limit")
    void body() throws Exception {
        reply = textReply("Hello", "stop");
        AiToolDescriptor tool = new AiToolDescriptor(
                "core.my_leave_balances",
                "Leave balances",
                JsonNodeFactory.instance.objectNode().put("type", "object"));
        client.send(request(List.of(tool)));

        JsonNode sent = json.readTree(body.get());
        assertThat(sent.path("model").asText()).isEqualTo("gpt-helper");
        assertThat(sent.path("max_completion_tokens").asInt()).isEqualTo(1024);
        assertThat(sent.has("max_tokens")).isFalse();
        assertThat(sent.has("stream")).isFalse();
        assertThat(sent.path("tool_choice").asText()).isEqualTo("auto");
        assertThat(sent.path("messages").path(0).path("role").asText()).isEqualTo("system");
        assertThat(sent.path("messages").path(0).path("content").asText()).isEqualTo("Be helpful.");
        assertThat(sent.path("messages").path(1).path("role").asText()).isEqualTo("user");
        assertThat(sent.path("messages").path(1).path("content").asText()).isEqualTo("How much leave?");
        JsonNode t = sent.path("tools").path(0);
        assertThat(t.path("type").asText()).isEqualTo("function");
        assertThat(t.path("function").path("name").asText()).isEqualTo("core__my_leave_balances");
        assertThat(t.path("function").path("description").asText()).isEqualTo("Leave balances");
        assertThat(t.path("function").path("parameters").path("type").asText()).isEqualTo("object");
    }

    @Test
    @DisplayName("no tools: neither tools nor tool_choice is sent")
    void noTools() throws Exception {
        reply = textReply("Hello", "stop");
        client.send(request(List.of()));

        JsonNode sent = json.readTree(body.get());
        assertThat(sent.has("tools")).isFalse();
        assertThat(sent.has("tool_choice")).isFalse();
    }

    @Test
    @DisplayName("an assistant tool call and its tool result are sent back in the OpenAI shape, the name with __")
    void toolRoundTripMessages() throws Exception {
        reply = textReply("Done", "stop");
        AiModelResponse.ToolCall call = new AiModelResponse.ToolCall("call_1", "core.holidays", "{\"from\":\"x\"}");
        client.send(new AiModelRequest(
                "Be helpful.",
                List.of(
                        AiModelRequest.Message.user("Holidays?"),
                        AiModelRequest.Message.assistantToolCalls(List.of(call)),
                        AiModelRequest.Message.tool("call_1", "{\"rows\":[]}")),
                List.of(),
                1024,
                Duration.ofSeconds(5)));

        JsonNode messages = json.readTree(body.get()).path("messages");
        JsonNode assistant = messages.path(2);
        assertThat(assistant.path("role").asText()).isEqualTo("assistant");
        assertThat(assistant.path("content").isNull()).isTrue();
        assertThat(assistant.path("tool_calls").path(0).path("id").asText()).isEqualTo("call_1");
        assertThat(assistant.path("tool_calls").path(0).path("type").asText()).isEqualTo("function");
        assertThat(assistant
                        .path("tool_calls")
                        .path(0)
                        .path("function")
                        .path("name")
                        .asText())
                .isEqualTo("core__holidays");
        assertThat(assistant
                        .path("tool_calls")
                        .path(0)
                        .path("function")
                        .path("arguments")
                        .asText())
                .isEqualTo("{\"from\":\"x\"}");
        JsonNode tool = messages.path(3);
        assertThat(tool.path("role").asText()).isEqualTo("tool");
        assertThat(tool.path("tool_call_id").asText()).isEqualTo("call_1");
        assertThat(tool.path("content").asText()).isEqualTo("{\"rows\":[]}");
    }

    @Test
    @DisplayName("a tool_calls reply is parsed, the name comes back with the dot and arguments stay a string")
    void toolCallsReply() {
        reply =
                """
                {"choices":[{"finish_reason":"tool_calls","message":{"role":"assistant","content":null,
                 "tool_calls":[{"id":"call_9","type":"function",
                   "function":{"name":"core__my_leave_balances","arguments":"{\\"asOf\\":\\"2026-11-23\\"}"}}]}}],
                 "usage":{"prompt_tokens":120,"completion_tokens":15}}
                """;
        AiModelResponse response = client.send(request(List.of()));

        assertThat(response.kind()).isEqualTo(AiModelResponse.Kind.TOOL_CALLS);
        assertThat(response.toolCalls()).hasSize(1);
        AiModelResponse.ToolCall call = response.toolCalls().get(0);
        assertThat(call.id()).isEqualTo("call_9");
        assertThat(call.name()).isEqualTo("core.my_leave_balances");
        assertThat(call.argumentsJson()).isEqualTo("{\"asOf\":\"2026-11-23\"}");
        assertThat(response.promptTokens()).isEqualTo(120);
        assertThat(response.completionTokens()).isEqualTo(15);
    }

    @Test
    @DisplayName("a text reply is parsed with its token counts")
    void textReplyParsed() {
        reply = textReply("You have 12 days.", "stop");
        AiModelResponse response = client.send(request(List.of()));

        assertThat(response.kind()).isEqualTo(AiModelResponse.Kind.TEXT);
        assertThat(response.text()).isEqualTo("You have 12 days.");
        assertThat(response.promptTokens()).isEqualTo(10);
        assertThat(response.completionTokens()).isEqualTo(5);
    }

    @Test
    @DisplayName("finish_reason length: the text ends with the cut-short note")
    void lengthCut() {
        reply = textReply("You have", "length");
        AiModelResponse response = client.send(request(List.of()));

        assertThat(response.kind()).isEqualTo(AiModelResponse.Kind.TEXT);
        assertThat(response.text()).isEqualTo("You have… (answer cut short)");
    }

    @Test
    @DisplayName("400 with code content_filter is the filtered result, not an exception")
    void promptFiltered() {
        status = 400;
        reply =
                """
                {"error":{"code":"content_filter","message":"The response was filtered",
                 "innererror":{"code":"ResponsibleAIPolicyViolation"}}}
                """;
        assertThat(client.send(request(List.of())).kind()).isEqualTo(AiModelResponse.Kind.FILTERED);
    }

    @Test
    @DisplayName("finish_reason content_filter is the filtered result, not an exception")
    void answerFiltered() {
        reply =
                """
                {"choices":[{"finish_reason":"content_filter","message":{"role":"assistant","content":null}}]}
                """;
        assertThat(client.send(request(List.of())).kind()).isEqualTo(AiModelResponse.Kind.FILTERED);
    }

    @Test
    @DisplayName("HTTP 429, 500 and another 400 are AiModelException")
    void httpErrors() {
        for (int code : new int[] {429, 500, 400}) {
            status = code;
            reply = "{\"error\":{\"code\":\"other\",\"message\":\"no\"}}";
            assertThatThrownBy(() -> client.send(request(List.of())))
                    .as("HTTP %d", code)
                    .isInstanceOf(AiModelException.class)
                    .hasMessageContaining(String.valueOf(code));
        }
    }

    @Test
    @DisplayName("a call slower than its timeout is AiModelException")
    void timeout() {
        delayMillis = 2000;
        reply = textReply("late", "stop");
        assertThatThrownBy(() -> client.send(new AiModelRequest(
                        "Be helpful.",
                        List.of(AiModelRequest.Message.user("Hi")),
                        List.of(),
                        1024,
                        Duration.ofMillis(300))))
                .isInstanceOf(AiModelException.class)
                .hasMessageContaining("did not answer");
    }

    @Test
    @DisplayName("the token is fetched once and reused until five minutes before it expires")
    void tokenCached() {
        AtomicInteger fetches = new AtomicInteger();
        Instant start = Instant.parse("2026-11-23T10:00:00Z");
        MutableClock clock = new MutableClock(start);
        TokenCredential credential = ctx -> Mono.fromSupplier(() -> new AccessToken(
                "t" + fetches.incrementAndGet(), OffsetDateTime.ofInstant(start.plusSeconds(3600), ZoneOffset.UTC)));
        AzureOpenAiModelClient.CachedToken token = new AzureOpenAiModelClient.CachedToken(
                credential, "https://cognitiveservices.azure.com/.default", clock);

        assertThat(token.get()).isEqualTo("t1");
        clock.now = start.plusSeconds(3600 - 301);
        assertThat(token.get()).isEqualTo("t1");
        clock.now = start.plusSeconds(3600 - 300);
        assertThat(token.get()).isEqualTo("t2");
        assertThat(fetches.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("tool names: dot to __ and back")
    void wireNames() {
        assertThat(AzureOpenAiModelClient.toWire("payroll.my_payslips")).isEqualTo("payroll__my_payslips");
        assertThat(AzureOpenAiModelClient.fromWire("payroll__my_payslips")).isEqualTo("payroll.my_payslips");
    }

    private static AiModelRequest request(List<AiToolDescriptor> tools) {
        return new AiModelRequest(
                "Be helpful.",
                List.of(AiModelRequest.Message.user("How much leave?")),
                tools,
                1024,
                Duration.ofSeconds(5));
    }

    private static String textReply(String text, String finish) {
        return "{\"choices\":[{\"finish_reason\":\"" + finish + "\",\"message\":{\"role\":\"assistant\",\"content\":\""
                + text + "\"}}],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":5}}";
    }

    private static final class MutableClock extends Clock {
        volatile Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
