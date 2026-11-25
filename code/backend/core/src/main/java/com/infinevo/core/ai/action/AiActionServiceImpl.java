package com.infinevo.core.ai.action;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.infinevo.core.ai.AiAccessGate;
import com.infinevo.core.ai.AiActionIntent;
import com.infinevo.core.ai.AiActionIntentRepository;
import com.infinevo.core.ai.AiCaller;
import com.infinevo.core.ai.AiChatTurn;
import com.infinevo.core.ai.AiDisabledException;
import com.infinevo.core.ai.AiIntentStatus;
import com.infinevo.core.ai.AiProposedAction;
import com.infinevo.core.ai.AiToolContext;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.shared.audit.AuditWriter;
import com.infinevo.shared.audit.AuditWriter.AuditChange;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.entitlement.EntitlementService;
import com.infinevo.shared.entitlement.PlatformModule;
import com.infinevo.shared.logging.MdcLoggingContext;
import com.infinevo.shared.tenant.TenantContext;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Implementation of {@link AiActionService} (W-114.8).
 *
 * <p><strong>Confirm</strong> runs the re-checks of the spec in order, inside one transaction holding the intent's row
 * lock ({@link AiActionIntentRepository#findByIdAndTenantIdAndUserIdForUpdate}): the intent is the caller's
 * ({@code 404}); a confirmed one answers its stored result and runs nothing; a cancelled, expired or failed one is
 * refused; one past its time becomes {@code EXPIRED}; the helper must be on, the caller must hold the executor's action
 * and, where it names one, the customer its module; the caller must have an employee record when the executor needs
 * one; then the executor runs the <em>stored</em> payload. A refusal by the service rolls that transaction back -
 * nothing it wrote survives - and a second short transaction, under the lock again, marks the intent {@code FAILED}
 * with the reason.
 *
 * <p><strong>Audit</strong>: propose, confirm, cancel, expire and fail each write one {@code core.audit_log} row for
 * {@code core.ai_action_intent} through {@link AuditWriter}, which fills the actor and the request's correlation id.
 * The payload and the result are never copied into it. {@code result.traceId} holds the same correlation id, so the
 * confirm row, the leave request's own row and the intent all carry one trace id.
 */
@Service
public class AiActionServiceImpl implements AiActionService {

    /** How long a proposal waits for the user (spec "Decisions"). */
    static final Duration TTL = Duration.ofMinutes(15);

    static final String ENTITY_SCHEMA = "core";
    static final String ENTITY_TABLE = "ai_action_intent";

    private static final Logger log = LoggerFactory.getLogger(AiActionServiceImpl.class);
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private final AiActionIntentRepository intents;
    private final Map<String, AiActionExecutor> executors;
    private final AiAccessGate accessGate;
    private final PermissionService permissionService;
    private final EntitlementService entitlementService;
    private final EmployeeService employeeService;
    private final AuditWriter auditWriter;
    private final TransactionOperations tx;
    private final Clock clock;

    @Autowired
    public AiActionServiceImpl(
            AiActionIntentRepository intents,
            List<AiActionExecutor> executors,
            AiAccessGate accessGate,
            PermissionService permissionService,
            EntitlementService entitlementService,
            EmployeeService employeeService,
            AuditWriter auditWriter,
            PlatformTransactionManager transactionManager) {
        this(
                intents,
                executors,
                accessGate,
                permissionService,
                entitlementService,
                employeeService,
                auditWriter,
                new TransactionTemplate(transactionManager),
                Clock.systemUTC());
    }

    AiActionServiceImpl(
            AiActionIntentRepository intents,
            List<AiActionExecutor> executors,
            AiAccessGate accessGate,
            PermissionService permissionService,
            EntitlementService entitlementService,
            EmployeeService employeeService,
            AuditWriter auditWriter,
            TransactionOperations tx,
            Clock clock) {
        this.intents = Objects.requireNonNull(intents, "intents must not be null");
        this.executors = (executors == null ? List.<AiActionExecutor>of() : executors)
                .stream()
                        .collect(Collectors.toMap(
                                AiActionExecutor::actionCode,
                                Function.identity(),
                                (a, b) -> {
                                    throw new IllegalStateException(
                                            "Two AI action executors carry out '" + a.actionCode() + "': "
                                                    + a.getClass().getName() + " and "
                                                    + b.getClass().getName());
                                },
                                LinkedHashMap::new));
        this.accessGate = Objects.requireNonNull(accessGate, "accessGate must not be null");
        this.permissionService = Objects.requireNonNull(permissionService, "permissionService must not be null");
        this.entitlementService = Objects.requireNonNull(entitlementService, "entitlementService must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
        this.auditWriter = Objects.requireNonNull(auditWriter, "auditWriter must not be null");
        this.tx = Objects.requireNonNull(tx, "tx must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    // ---- propose ----

    @Override
    public AiProposedAction propose(AiToolContext ctx, String actionCode, JsonNode payload) {
        Objects.requireNonNull(ctx, "ctx must not be null");
        AiActionExecutor executor = executor(actionCode);
        if (payload == null || !payload.isObject()) {
            throw new IllegalArgumentException("payload must be a JSON object");
        }
        AiChatTurn turn = AiChatTurn.current()
                .orElseThrow(() -> new IllegalStateException(
                        "An action can be proposed only while a chat message" + " is answered"));
        if (!turn.tenantId().equals(ctx.tenantId())
                || !turn.userId().equals(ctx.userId())
                || !ctx.tenantId().equals(TenantContext.require())) {
            throw new IllegalStateException("The context is not the caller of the chat being answered");
        }
        if (turn.proposed() != null) {
            throw new IllegalStateException("An action was already proposed in this message");
        }
        AiProposedAction card = tx.execute(s -> {
            AiActionIntent intent = intents.save(new AiActionIntent(
                    ctx.tenantId(),
                    turn.sessionId(),
                    ctx.userId(),
                    null,
                    executor.actionCode(),
                    payload.deepCopy(),
                    clock.instant().plus(TTL)));
            Map<String, String> created = new LinkedHashMap<>();
            created.put("action_code", intent.getActionCode());
            created.put("status", intent.getStatus().name());
            created.put("session_id", intent.getSessionId().toString());
            created.put("expires_at", intent.getExpiresAt().toString());
            auditWriter.write(new AuditChange(
                    "INSERT",
                    ENTITY_SCHEMA,
                    ENTITY_TABLE,
                    intent.getId().toString(),
                    List.copyOf(created.keySet()),
                    null,
                    created));
            AiActionDescription d = executor.describe(ctx, intent.getPayload());
            return new AiProposedAction(
                    intent.getId(),
                    intent.getActionCode(),
                    d.summary(),
                    d.fields(),
                    intent.getStatus(),
                    intent.getExpiresAt());
        });
        turn.recordProposal(card);
        log.info("AI action proposed: intent={} action={} session={}", card.intentId(), actionCode, turn.sessionId());
        return card;
    }

    // ---- get ----

    @Override
    public AiActionView get(UUID intentId) {
        Caller caller = caller();
        return tx.execute(s -> {
            AiActionIntent intent = lock(caller, intentId);
            if (intent.getStatus() == AiIntentStatus.PROPOSED && isPast(intent)) {
                move(intent, AiIntentStatus.EXPIRED);
            }
            AiActionExecutor executor = executor(intent.getActionCode());
            AiActionDescription d = executor.describe(context(caller, ownEmployeeId()), intent.getPayload());
            return new AiActionView(
                    intent.getId(),
                    intent.getActionCode(),
                    d.summary(),
                    d.fields(),
                    intent.getStatus(),
                    intent.getExpiresAt(),
                    intent.getResult());
        });
    }

    // ---- confirm ----

    @Override
    public AiActionOutcome confirm(UUID intentId) {
        Caller caller = caller();
        Attempt attempt;
        try {
            attempt = tx.execute(s -> runOnce(caller, intentId));
        } catch (Refused refused) {
            // The executor's transaction rolled back; mark the intent FAILED in a short one of its own.
            attempt = tx.execute(s -> markFailed(caller, intentId, refused.reason));
        }
        return Objects.requireNonNull(attempt, "attempt").answer();
    }

    private Attempt runOnce(Caller caller, UUID intentId) {
        AiActionIntent intent = lock(caller, intentId); // check 1
        switch (intent.getStatus()) {
            case CONFIRMED -> {
                return Attempt.of(outcome(intent)); // check 2: nothing runs again
            }
            case CANCELLED -> throw new AiActionCancelledException(); // check 3
            case EXPIRED -> throw new AiActionExpiredException();
            case FAILED -> throw new AiActionFailedException(reasonOf(intent));
            case PROPOSED -> {
                // carry on
            }
        }
        if (isPast(intent)) { // check 4
            move(intent, AiIntentStatus.EXPIRED);
            return Attempt.refused(new AiActionExpiredException());
        }
        if (!accessGate.status().enabled()) { // check 5
            throw new AiDisabledException();
        }
        AiActionExecutor executor = executor(intent.getActionCode());
        if (!permissionService.holds(executor.requiredAction())) { // check 6
            throw new AccessDeniedException("The caller no longer holds " + executor.requiredAction());
        }
        PlatformModule module = executor.requiredModule();
        if (module != null && !entitlementService.holds(module)) { // check 6a
            throw new AccessDeniedException("The customer does not hold the " + module + " module");
        }
        UUID employeeId = ownEmployeeId();
        if (executor.requiresEmployeeProfile() && employeeId == null) { // check 7
            throw new AccessDeniedException("No employee profile found for caller");
        }

        JsonNode written;
        try {
            written = executor.execute(context(caller, employeeId), intent.getPayload()); // check 8
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new Refused(e.getMessage());
        }

        ObjectNode result =
                written != null && written.isObject() ? ((ObjectNode) written).deepCopy() : NODES.objectNode();
        result.put("traceId", MDC.get(MdcLoggingContext.CORRELATION_ID_KEY));
        intent.setResult(result);
        intent.setConfirmedAt(clock.instant());
        move(intent, AiIntentStatus.CONFIRMED); // check 9
        log.info("AI action confirmed: intent={} action={}", intent.getId(), intent.getActionCode());
        return Attempt.of(outcome(intent));
    }

    private Attempt markFailed(Caller caller, UUID intentId, String reason) {
        AiActionIntent intent = lock(caller, intentId);
        switch (intent.getStatus()) {
            case PROPOSED -> {
                intent.setResult(NODES.objectNode().put("reason", reason));
                move(intent, AiIntentStatus.FAILED);
                log.info("AI action refused at confirm: intent={} action={}", intent.getId(), intent.getActionCode());
                return Attempt.refused(new AiActionFailedException(reason));
            }
            case CONFIRMED -> {
                // Another confirm carried it out between the two transactions.
                return Attempt.of(outcome(intent));
            }
            case FAILED -> {
                return Attempt.refused(new AiActionFailedException(reasonOf(intent)));
            }
            case CANCELLED -> {
                return Attempt.refused(new AiActionCancelledException());
            }
            default -> {
                return Attempt.refused(new AiActionExpiredException());
            }
        }
    }

    // ---- cancel ----

    @Override
    public AiActionOutcome cancel(UUID intentId) {
        Caller caller = caller();
        Attempt attempt = tx.execute(s -> {
            AiActionIntent intent = lock(caller, intentId);
            switch (intent.getStatus()) {
                case CANCELLED -> {
                    return Attempt.of(outcome(intent));
                }
                case CONFIRMED -> throw new AiActionConfirmedException();
                case EXPIRED -> throw new AiActionExpiredException();
                case FAILED -> throw new AiActionFailedException(reasonOf(intent));
                case PROPOSED -> {
                    // carry on
                }
            }
            if (isPast(intent)) {
                move(intent, AiIntentStatus.EXPIRED);
                return Attempt.refused(new AiActionExpiredException());
            }
            move(intent, AiIntentStatus.CANCELLED);
            log.info("AI action cancelled: intent={} action={}", intent.getId(), intent.getActionCode());
            return Attempt.of(outcome(intent));
        });
        return Objects.requireNonNull(attempt, "attempt").answer();
    }

    // ---- helpers ----

    /** Moves a {@code PROPOSED} intent to {@code to}, saves it, and writes the one audit row of the step. */
    private void move(AiActionIntent intent, AiIntentStatus to) {
        AiIntentStatus from = intent.getStatus();
        intent.setStatus(to);
        intents.save(intent);
        auditWriter.write(new AuditChange(
                "UPDATE",
                ENTITY_SCHEMA,
                ENTITY_TABLE,
                intent.getId().toString(),
                List.of("status"),
                Map.of("status", from.name()),
                Map.of("status", to.name())));
    }

    private AiActionIntent lock(Caller caller, UUID intentId) {
        if (intentId == null) {
            throw new AiActionNotFoundException();
        }
        return intents.findByIdAndTenantIdAndUserIdForUpdate(intentId, caller.tenantId(), caller.userId())
                .orElseThrow(AiActionNotFoundException::new);
    }

    private AiActionExecutor executor(String actionCode) {
        AiActionExecutor executor = actionCode == null ? null : executors.get(actionCode);
        if (executor == null) {
            throw new IllegalArgumentException("No AI action executor carries out '" + actionCode + "'");
        }
        return executor;
    }

    private boolean isPast(AiActionIntent intent) {
        return !clock.instant().isBefore(intent.getExpiresAt());
    }

    private UUID ownEmployeeId() {
        return employeeService.currentEmployee().map(EmployeeResponse::id).orElse(null);
    }

    private static AiToolContext context(Caller caller, UUID employeeId) {
        return new AiToolContext(caller.tenantId(), caller.userId(), employeeId);
    }

    private static AiActionOutcome outcome(AiActionIntent intent) {
        return new AiActionOutcome(intent.getId(), intent.getActionCode(), intent.getStatus(), intent.getResult());
    }

    private static String reasonOf(AiActionIntent intent) {
        JsonNode result = intent.getResult();
        return result == null ? null : result.path("reason").asText(null);
    }

    /** The bound tenant and the token subject; {@code @RequiresAction} has already refused a caller with neither. */
    private static Caller caller() {
        UUID tenantId = TenantContext.require();
        UUID userId = AiCaller.currentUserId()
                .orElseThrow(() -> new AccessDeniedException("No signed-in user with a UUID subject"));
        return new Caller(tenantId, userId);
    }

    private record Caller(UUID tenantId, UUID userId) {}

    /**
     * What one transaction decided: an outcome to answer, or a refusal to throw once it has committed - so the
     * {@code EXPIRED} and {@code FAILED} states it wrote are kept.
     */
    private record Attempt(AiActionOutcome outcome, RuntimeException refusal) {

        static Attempt of(AiActionOutcome outcome) {
            return new Attempt(outcome, null);
        }

        static Attempt refused(RuntimeException refusal) {
            return new Attempt(null, refusal);
        }

        AiActionOutcome answer() {
            if (refusal != null) {
                throw refusal;
            }
            return outcome;
        }
    }

    /** The service refused the payload; thrown out of the executor's transaction so it rolls back. */
    private static final class Refused extends RuntimeException {

        private static final long serialVersionUID = 1L;

        final String reason;

        Refused(String reason) {
            super(reason, null, false, false);
            this.reason = reason;
        }
    }
}
