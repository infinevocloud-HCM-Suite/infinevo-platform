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
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
 * <p>The order: the chat must be the caller's ({@code 404}); then the per-user rate limit ({@link AiRateLimiter},
 * {@code 429 AI_RATE_LIMITED}); then {@link AiAccessGate#consume()}, once, before any
 * model call (rule 8); then the masked question is stored; then up to five tool rounds. Each stored message is its own
 * short transaction, and no transaction is open while the model is called, so a slow model never holds a database
 * connection. Tools run in this thread as the caller, only through {@link AiToolRegistry#execute}, and only the tools
 * {@link AiToolRegistry#toolsFor} offered may run (rule 3).
 *
 * <p>From W-114.8 a tool may propose an action ({@link AiChatTurn}). The answer then carries the action's card as
 * {@code proposedAction}, and the intent's {@code message_id} is set to the stored answer. When the history is sent to
 * the model again, each assistant message whose intent is no longer {@code PROPOSED} carries one fixed line saying what
 * became of it ({@link #statusNote}) - in the copy sent only, never in the stored message.
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

    static final String NOTE_DECLINED = "[The user declined this action. This is their choice, not an error."
            + " Do not propose it again unless they ask.]";
    static final String NOTE_CONFIRMED = "[The user confirmed this action and it was carried out.]";
    static final String NOTE_EXPIRED = "[This proposal expired and was not carried out.]";
    static final String NOTE_FAILED = "[The user confirmed, but the action was refused: %s]";

    private static final Logger log = LoggerFactory.getLogger(AiChatServiceImpl.class);

    private static final java.util.regex.Pattern UUID_IN_TEXT = java.util.regex.Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final List<AiMessageRole> HISTORY_ROLES = List.of(AiMessageRole.USER, AiMessageRole.ASSISTANT);

    private final AiSessionRepository sessions;
    private final AiMessageRepository messages;
    private final AiActionIntentRepository intents;
    private final AiAccessGate accessGate;
    private final AiRateLimiter rateLimiter;
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
            AiActionIntentRepository intents,
            AiAccessGate accessGate,
            AiRateLimiter rateLimiter,
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
                intents,
                accessGate,
                rateLimiter,
                registry,
                model,
                masking,
                systemPrompt,
                employeeService,
                tenantClock,
                new TransactionTemplate(transactionManager),
                Clock.systemUTC());
    }

    /** As W-114.4 built it: no intents, so no proposal is linked and no status line is added. */
    AiChatServiceImpl(
            AiSessionRepository sessions,
            AiMessageRepository messages,
            AiAccessGate accessGate,
            AiRateLimiter rateLimiter,
            AiToolRegistry registry,
            AiModelClient model,
            AiMaskingFilter masking,
            AiSystemPrompt systemPrompt,
            EmployeeService employeeService,
            TenantClock tenantClock,
            TransactionOperations tx,
            Clock clock) {
        this(
                sessions,
                messages,
                null,
                accessGate,
                rateLimiter,
                registry,
                model,
                masking,
                systemPrompt,
                employeeService,
                tenantClock,
                tx,
                clock);
    }

    AiChatServiceImpl(
            AiSessionRepository sessions,
            AiMessageRepository messages,
            AiActionIntentRepository intents,
            AiAccessGate accessGate,
            AiRateLimiter rateLimiter,
            AiToolRegistry registry,
            AiModelClient model,
            AiMaskingFilter masking,
            AiSystemPrompt systemPrompt,
            EmployeeService employeeService,
            TenantClock tenantClock,
            TransactionOperations tx,
            Clock clock) {
        this.intents = intents;
        this.sessions = Objects.requireNonNull(sessions, "sessions must not be null");
        this.messages = Objects.requireNonNull(messages, "messages must not be null");
        this.accessGate = Objects.requireNonNull(accessGate, "accessGate must not be null");
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter must not be null");
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

        AiSession found = tx.execute(s -> {
            AiSession session = sessions.findByIdAndTenantIdAndUserId(sessionId, tenantId, userId)
                    .orElseThrow(() -> new AiSessionNotFoundException(sessionId));
            // Before consume(): a refused message is not counted, stores no row and calls no model (rule 10).
            rateLimiter.check(tenantId, userId);
            return session;
        });
        accessGate.consume();

        Run run = new Run(found);
        String maskedQuestion = masking.mask(question);
        Setup setup = tx.execute(s -> {
            List<AiMessage> earlier = new ArrayList<>(messages.findByTenantIdAndSessionIdAndRoleInOrderByCreatedAtDesc(
                    tenantId, sessionId, HISTORY_ROLES, PageRequest.of(0, HISTORY - 1)));
            Collections.reverse(earlier);
            Map<UUID, String> notes = statusNotes(tenantId, earlier);
            store(run, AiMessageRole.USER, maskedQuestion);
            LocalDate today = tenantClock.today();
            Optional<EmployeeResponse> me = employeeService.currentEmployee();
            return new Setup(
                    earlier,
                    notes,
                    today,
                    me.map(EmployeeResponse::id).orElse(null),
                    me.map(EmployeeResponse::firstName).orElse(null));
        });

        AiToolContext caller = new AiToolContext(tenantId, userId, setup.employeeId());
        List<AiToolDescriptor> tools = registry.toolsFor(caller);
        Set<String> offered = tools.stream().map(AiToolDescriptor::name).collect(Collectors.toSet());
        String prompt = systemPrompt.build(setup.today(), setup.firstName());

        List<AiModelRequest.Message> conversation = new ArrayList<>();
        for (AiMessage m : setup.history()) {
            String text = masking.mask(m.getContent());
            String note = setup.notes().get(m.getId());
            if (note != null) {
                text = text + "\n\n" + note;
            }
            conversation.add(
                    m.getRole() == AiMessageRole.USER
                            ? AiModelRequest.Message.user(text)
                            : AiModelRequest.Message.assistant(text));
        }
        conversation.add(AiModelRequest.Message.user(maskedQuestion));

        try (AiChatTurn turn = AiChatTurn.open(tenantId, userId, sessionId)) {
            AiMessage answer = loop(run, prompt, conversation, tools, offered, caller, deadline);
            AiProposedAction proposed = turn.proposed();
            if (proposed != null) {
                link(proposed.intentId(), answer, tenantId, userId);
            }
            run.log("answered", started, clock.instant());
            return new AiChatResponse(AiChatResponse.Message.of(answer), proposed);
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
                        String id =
                                c.id() == null || c.id().isBlank() ? "call_" + run.toolRounds + "_" + (i + 1) : c.id();
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

    /** Sets the intent's {@code message_id} to the stored answer: the link the messages list reads back. */
    private void link(UUID intentId, AiMessage answer, UUID tenantId, UUID userId) {
        if (intents == null) {
            return;
        }
        boolean linked = Boolean.TRUE.equals(tx.execute(s -> intents.findById(intentId)
                .filter(i -> tenantId.equals(i.getTenantId()) && userId.equals(i.getUserId()))
                .map(i -> {
                    i.setMessageId(answer.getId());
                    intents.save(i);
                    return true;
                })
                .orElse(false)));
        if (!linked) {
            log.warn(
                    "AI intent {} was not linked to answer {}: the card will not show after a reload",
                    intentId,
                    answer.getId());
        }
    }

    /**
     * The status line of each assistant message in the history whose intent is no longer {@code PROPOSED}, keyed by
     * message id (W-114.8, "What the model is told"). An intent past its time still {@code PROPOSED} is told as expired.
     */
    private Map<UUID, String> statusNotes(UUID tenantId, List<AiMessage> history) {
        if (intents == null) {
            return Map.of();
        }
        List<UUID> assistantIds = history.stream()
                .filter(m -> m.getRole() == AiMessageRole.ASSISTANT && m.getId() != null)
                .map(AiMessage::getId)
                .toList();
        if (assistantIds.isEmpty()) {
            return Map.of();
        }
        Instant now = clock.instant();
        Map<UUID, String> notes = new HashMap<>();
        List<AiActionIntent> found = intents.findByTenantIdAndMessageIdIn(tenantId, assistantIds);
        for (AiActionIntent intent : found == null ? List.<AiActionIntent>of() : found) {
            String note = statusNote(intent, now);
            if (note != null && intent.getMessageId() != null) {
                notes.merge(intent.getMessageId(), note, (a, b) -> a + "\n" + b);
            }
        }
        return notes;
    }

    /** The fixed line for the intent's state, or {@code null} while it is still waiting for the user. */
    String statusNote(AiActionIntent intent, Instant now) {
        return switch (intent.getStatus()) {
            case PROPOSED -> !now.isBefore(intent.getExpiresAt()) ? NOTE_EXPIRED : null;
            case CANCELLED -> NOTE_DECLINED;
            case CONFIRMED -> NOTE_CONFIRMED;
            case EXPIRED -> NOTE_EXPIRED;
            case FAILED -> String.format(NOTE_FAILED, masking.mask(failureReason(intent.getResult())));
        };
    }

    private static String failureReason(JsonNode result) {
        String reason = result == null ? null : result.path("reason").asText(null);
        if (reason == null || reason.isBlank()) {
            return "no reason was given";
        }
        // A service message may name a row ("Leave type not found: <uuid>"); the model never sees an id (W-114.4).
        return UUID_IN_TEXT.matcher(reason).replaceAll("[id]");
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

    private record Setup(
            List<AiMessage> history, Map<UUID, String> notes, LocalDate today, UUID employeeId, String firstName) {}

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
