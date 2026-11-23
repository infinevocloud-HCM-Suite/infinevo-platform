package com.infinevo.core.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.tenant.TenantClock;
import com.infinevo.shared.tenant.TenantContext;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Implementation of {@link AiChatService} (W-114.4, spec "Flow", "Rules" and "Limits").
 *
 * <p>The order: the chat must be the caller's ({@code 404}); then {@link AiAccessGate#consume()}, once, before any
 * model call (rule 8); then the masked question is stored; then up to five tool rounds. Each stored message is its own
 * short transaction, and no transaction is open while the model is called, so a slow model never holds a database
 * connection. Tools run in this thread as the caller, only through {@link AiToolRegistry#execute}, and only the tools
 * {@link AiToolRegistry#toolsFor} offered may run (rule 3).
 *
 * <p>Logged: session id, model calls, tool rounds, tool names, token counts, duration and outcome. Never the question,
 * a tool result or the answer.
 */
@Service
public class AiChatServiceImpl implements AiChatService {

    static final int HISTORY = 20;
    static final int MAX_TOOL_ROUNDS = 5;
    static final int MAX_OUTPUT_TOKENS = 1024;
    static final Duration CALL_TIMEOUT = Duration.ofSeconds(30);
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
    static final int MAX_TOOL_RESULT = 8000;

    static final String FILTERED_ANSWER = "I can't help with that request.";
    static final String TOO_MANY_ROUNDS = "I could not finish that. Please ask in a smaller step.";
    static final String MORE_ROWS = "…(more rows not shown)";
    static final String NOT_AVAILABLE = "This tool is not available.";
    static final String BAD_ARGUMENTS = "The arguments were not valid JSON, so the tool was not run.";
    static final String TOOL_FAILED = "The tool failed.";

    private static final Logger log = LoggerFactory.getLogger(AiChatServiceImpl.class);
    private static final List<AiMessageRole> HISTORY_ROLES = List.of(AiMessageRole.USER, AiMessageRole.ASSISTANT);

    private final AiSessionRepository sessions;
    private final AiMessageRepository messages;
    private final AiAccessGate accessGate;
    private final AiToolRegistry registry;
    private final AiModelClient model;
    private final AiMaskingFilter masking;
    private final AiSystemPrompt systemPrompt;
    private final EmployeeService employeeService;
    private final TenantClock tenantClock;
    private final TransactionOperations tx;
    private final Clock clock;
    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    public AiChatServiceImpl(
            AiSessionRepository sessions,
            AiMessageRepository messages,
            AiAccessGate accessGate,
            AiToolRegistry registry,
            AiModelClient model,
            AiMaskingFilter masking,
            AiSystemPrompt systemPrompt,
            EmployeeService employeeService,
            TenantClock tenantClock,
            PlatformTransactionManager transactionManager) {
        this(
                sessions,
                messages,
                accessGate,
                registry,
                model,
                masking,
                systemPrompt,
                employeeService,
                tenantClock,
                new TransactionTemplate(transactionManager),
                Clock.systemUTC());
    }

    AiChatServiceImpl(
            AiSessionRepository sessions,
            AiMessageRepository messages,
            AiAccessGate accessGate,
            AiToolRegistry registry,
            AiModelClient model,
            AiMaskingFilter masking,
            AiSystemPrompt systemPrompt,
            EmployeeService employeeService,
            TenantClock tenantClock,
            TransactionOperations tx,
            Clock clock) {
        this.sessions = Objects.requireNonNull(sessions, "sessions must not be null");
        this.messages = Objects.requireNonNull(messages, "messages must not be null");
        this.accessGate = Objects.requireNonNull(accessGate, "accessGate must not be null");
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.model = Objects.requireNonNull(model, "model must not be null");
        this.masking = Objects.requireNonNull(masking, "masking must not be null");
        this.systemPrompt = Objects.requireNonNull(systemPrompt, "systemPrompt must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
        this.tenantClock = Objects.requireNonNull(tenantClock, "tenantClock must not be null");
        this.tx = Objects.requireNonNull(tx, "tx must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public AiChatResponse send(UUID sessionId, String content) {
        String question = validate(content);
        if (sessionId == null) {
            throw new IllegalArgumentException("session id must be provided");
        }
        UUID tenantId = TenantContext.require();
        UUID userId = requireUser();
        Instant started = clock.instant();
        Instant deadline = started.plus(REQUEST_TIMEOUT);

        AiSession found = tx.execute(s -> sessions.findByIdAndTenantIdAndUserId(sessionId, tenantId, userId)
                .orElseThrow(() -> new AiSessionNotFoundException(sessionId)));
        accessGate.consume();

        Run run = new Run(found);
        String maskedQuestion = masking.mask(question);
        Setup setup = tx.execute(s -> {
            List<AiMessage> earlier = new ArrayList<>(messages.findByTenantIdAndSessionIdAndRoleInOrderByCreatedAtDesc(
                    tenantId, sessionId, HISTORY_ROLES, PageRequest.of(0, HISTORY - 1)));
            Collections.reverse(earlier);
            store(run, AiMessageRole.USER, maskedQuestion);
            LocalDate today = tenantClock.today();
            Optional<EmployeeResponse> me = employeeService.currentEmployee();
            return new Setup(earlier, today, me.map(EmployeeResponse::id).orElse(null), me.map(
                            EmployeeResponse::firstName)
                    .orElse(null));
        });

        AiToolContext caller = new AiToolContext(tenantId, userId, setup.employeeId());
        List<AiToolDescriptor> tools = registry.toolsFor(caller);
        Set<String> offered = tools.stream().map(AiToolDescriptor::name).collect(Collectors.toSet());
        String prompt = systemPrompt.build(setup.today(), setup.firstName());

        List<AiModelRequest.Message> conversation = new ArrayList<>();
        for (AiMessage m : setup.history()) {
            String text = masking.mask(m.getContent());
            conversation.add(
                    m.getRole() == AiMessageRole.USER
                            ? AiModelRequest.Message.user(text)
                            : AiModelRequest.Message.assistant(text));
        }
        conversation.add(AiModelRequest.Message.user(maskedQuestion));

        try {
            AiMessage answer = loop(run, prompt, conversation, tools, offered, caller, deadline);
            run.log("answered", started, clock.instant());
            return new AiChatResponse(AiChatResponse.Message.of(answer), null);
        } catch (RuntimeException e) {
            run.log("failed (" + e.getClass().getSimpleName() + ")", started, clock.instant());
            throw e;
        }
    }

    private AiMessage loop(
            Run run,
            String prompt,
            List<AiModelRequest.Message> conversation,
            List<AiToolDescriptor> tools,
            Set<String> offered,
            AiToolContext caller,
            Instant deadline) {
        while (true) {
            Duration left = Duration.between(clock.instant(), deadline);
            if (left.isNegative() || left.isZero()) {
                throw new AiModelException("The request ran out of time after " + run.modelCalls + " model call(s)");
            }
            Duration timeout = left.compareTo(CALL_TIMEOUT) < 0 ? left : CALL_TIMEOUT;
            AiModelResponse reply =
                    model.send(new AiModelRequest(prompt, conversation, tools, MAX_OUTPUT_TOKENS, timeout));
            run.modelCalls++;
            if (reply == null) {
                throw new AiModelException("The model client returned nothing");
            }
            run.promptTokens += reply.promptTokens();
            run.completionTokens += reply.completionTokens();

            switch (reply.kind()) {
                case FILTERED -> {
                    run.filtered = true;
                    return store(run, AiMessageRole.ASSISTANT, FILTERED_ANSWER);
                }
                case TEXT -> {
                    String text = masking.mask(reply.text());
                    if (text == null || text.isBlank()) {
                        throw new AiModelException("The model's answer was empty");
                    }
                    return store(run, AiMessageRole.ASSISTANT, text);
                }
                case TOOL_CALLS -> {
                    if (reply.toolCalls().isEmpty()) {
                        throw new AiModelException("The model asked for tools but named none");
                    }
                    if (run.toolRounds >= MAX_TOOL_ROUNDS) {
                        return store(run, AiMessageRole.ASSISTANT, TOO_MANY_ROUNDS);
                    }
                    run.toolRounds++;
                    List<AiModelResponse.ToolCall> calls = new ArrayList<>();
                    for (int i = 0; i < reply.toolCalls().size(); i++) {
                        AiModelResponse.ToolCall c = reply.toolCalls().get(i);
                        String id = c.id() == null || c.id().isBlank()
                                ? "call_" + run.toolRounds + "_" + (i + 1)
                                : c.id();
                        calls.add(new AiModelResponse.ToolCall(id, c.name(), c.argumentsJson()));
                    }
                    conversation.add(AiModelRequest.Message.assistantToolCalls(calls));
                    for (AiModelResponse.ToolCall call : calls) {
                        // A name the model made up is not logged: it is model output, like the answer.
                        run.toolNames.add(offered.contains(call.name()) ? call.name() : "(not offered)");
                        String result = runTool(call, offered, caller);
                        store(run, AiMessageRole.TOOL, result);
                        conversation.add(AiModelRequest.Message.tool(call.id(), result));
                    }
                }
            }
        }
    }

    /** Runs one tool call and answers the masked, cut JSON text the model is sent - an error object on any refusal. */
    private String runTool(AiModelResponse.ToolCall call, Set<String> offered, AiToolContext caller) {
        String name = call.name();
        if (name == null || !offered.contains(name)) {
            log.warn("AI tool refused: the model named a tool that was not offered to the caller");
            return error(NOT_AVAILABLE);
        }
        JsonNode input;
        try {
            input = parseArguments(call.argumentsJson());
        } catch (JsonProcessingException | IllegalArgumentException e) {
            log.warn("AI tool {} not run: its arguments were not a JSON object", name);
            return error(BAD_ARGUMENTS);
        }
        AiToolResult result;
        try {
            result = registry.execute(name, caller, input);
        } catch (RuntimeException e) {
            // The class only: an exception message may carry the data the tool read.
            log.warn("AI tool {} failed: {}", name, e.getClass().getName());
            return error(TOOL_FAILED);
        }
        if (result == null) {
            return error(TOOL_FAILED);
        }
        if (!result.ok()) {
            return error(result.error() == null || result.error().isBlank() ? TOOL_FAILED : result.error());
        }
        JsonNode data = result.data() == null ? JsonNodeFactory.instance.nullNode() : result.data();
        return cut(write(masking.mask(data)));
    }

    private JsonNode parseArguments(String arguments) throws JsonProcessingException {
        if (arguments == null || arguments.isBlank()) {
            return JsonNodeFactory.instance.objectNode();
        }
        JsonNode parsed = json.readTree(arguments);
        if (parsed == null || !parsed.isObject()) {
            throw new IllegalArgumentException("arguments are not a JSON object");
        }
        return parsed;
    }

    private String error(String message) {
        return cut(write(JsonNodeFactory.instance.objectNode().put("error", masking.mask(message))));
    }

    private String write(JsonNode node) {
        try {
            return json.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("A tool result could not be written as JSON", e);
        }
    }

    static String cut(String text) {
        if (text.length() <= MAX_TOOL_RESULT) {
            return text;
        }
        return text.substring(0, MAX_TOOL_RESULT) + MORE_ROWS;
    }

    /** One stored message in its own short transaction; the chat's last activity moves with it. */
    private AiMessage store(Run run, AiMessageRole role, String content) {
        return tx.execute(s -> {
            AiMessage saved =
                    messages.save(new AiMessage(run.session.getTenantId(), run.session.getId(), role, content));
            run.session.touch(clock.instant());
            run.session = sessions.save(run.session);
            return saved;
        });
    }

    private static String validate(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be empty");
        }
        if (content.length() > MAX_CONTENT) {
            throw new IllegalArgumentException("content must be at most " + MAX_CONTENT + " characters");
        }
        return content;
    }

    /** {@code @RequiresAction} has already refused a caller with no user; this is the same refusal, kept closed. */
    private static UUID requireUser() {
        return AiCaller.currentUserId()
                .orElseThrow(() -> new AccessDeniedException("No signed-in user with a UUID subject"));
    }

    private record Setup(List<AiMessage> history, LocalDate today, UUID employeeId, String firstName) {}

    /** What one request did, for the one log line it writes. Never holds text. */
    private static final class Run {
        AiSession session;
        int modelCalls;
        int toolRounds;
        int promptTokens;
        int completionTokens;
        boolean filtered;
        final Set<String> toolNames = new LinkedHashSet<>();

        Run(AiSession session) {
            this.session = session;
        }

        void log(String outcome, Instant started, Instant ended) {
            log.info(
                    "AI message {}: session={} modelCalls={} toolRounds={} tools={} promptTokens={}"
                            + " completionTokens={} contentFiltered={} durationMs={}",
                    outcome,
                    session.getId(),
                    modelCalls,
                    toolRounds,
                    toolNames,
                    promptTokens,
                    completionTokens,
                    filtered,
                    Duration.between(started, ended).toMillis());
        }
    }
}
