package com.infinevo.core.ai.action;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.infinevo.core.ai.AiAccessGate;
import com.infinevo.core.ai.AiActionField;
import com.infinevo.core.ai.AiActionIntent;
import com.infinevo.core.ai.AiActionIntentRepository;
import com.infinevo.core.ai.AiChatTurn;
import com.infinevo.core.ai.AiChatTurns;
import com.infinevo.core.ai.AiDisabledException;
import com.infinevo.core.ai.AiIntentStatus;
import com.infinevo.core.ai.AiProposedAction;
import com.infinevo.core.ai.AiStatus;
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
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionOperations;

/**
 * W-114.8 spec, Tests: {@link AiActionServiceImpl} - every row of the confirm re-check table, the module check (C7),
 * and one audit row per step with no payload (C10). A recording executor stands in for the leave one.
 */
class AiActionServiceImplTest {

    private static final String CODE = "core.test.action";

    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID otherUser = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final Instant base = Instant.parse("2026-11-25T09:00:00Z");
    private final List<AiActionIntent> store = new ArrayList<>();
    private final List<AuditChange> audit = new ArrayList<>();
    private final Set<String> granted = new HashSet<>();
    private final Set<PlatformModule> licensed = EnumSet.noneOf(PlatformModule.class);

    private boolean aiOn = true;
    private Optional<EmployeeResponse> me;
    private MovableClock clock;
    private RecordingExecutor executor;
    private AiActionServiceImpl service;

    @BeforeEach
    void setUp() {
        TenantContext.set(tenantId);
        signIn(userId);
        clock = new MovableClock(base);
        executor = new RecordingExecutor(null);
        me = Optional.of(mock(EmployeeResponse.class));
        when(me.get().id()).thenReturn(employeeId);
        granted.add("core.test.do");
        service = service(executor);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
        MDC.remove(MdcLoggingContext.CORRELATION_ID_KEY);
    }

    private AiActionServiceImpl service(AiActionExecutor... executors) {
        AiActionIntentRepository intents = mock(AiActionIntentRepository.class);
        when(intents.save(any(AiActionIntent.class))).thenAnswer(inv -> {
            AiActionIntent i = inv.getArgument(0);
            if (i.getId() == null) {
                ReflectionTestUtils.setField(i, "id", UUID.randomUUID());
                ReflectionTestUtils.setField(i, "createdAt", clock.instant());
                store.add(i);
            }
            return i;
        });
        when(intents.findByIdAndTenantIdAndUserIdForUpdate(any(), any(), any())).thenAnswer(inv -> store.stream()
                .filter(i -> i.getId().equals(inv.getArgument(0))
                        && i.getTenantId().equals(inv.getArgument(1))
                        && i.getUserId().equals(inv.getArgument(2)))
                .findFirst());
        AiAccessGate gate = mock(AiAccessGate.class);
        when(gate.status()).thenAnswer(inv -> new AiStatus(aiOn, 100, 1, "2026-11"));
        PermissionService permissions = mock(PermissionService.class);
        when(permissions.holds(anyString())).thenAnswer(inv -> granted.contains(inv.<String>getArgument(0)));
        EntitlementService entitlements = mock(EntitlementService.class);
        when(entitlements.holds(any(PlatformModule.class)))
                .thenAnswer(inv -> licensed.contains(inv.<PlatformModule>getArgument(0)));
        EmployeeService employees = mock(EmployeeService.class);
        when(employees.currentEmployee()).thenAnswer(inv -> me);
        AuditWriter writer = mock(AuditWriter.class);
        doAnswer(inv -> audit.add(inv.getArgument(0))).when(writer).write(any());
        return new AiActionServiceImpl(
                intents,
                List.of(executors),
                gate,
                permissions,
                entitlements,
                employees,
                writer,
                TransactionOperations.withoutTransaction(),
                clock);
    }

    private static void signIn(UUID user) {
        SecurityContextHolder.getContext()
                .setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user.toString(), null, List.of()));
    }

    private AiProposedAction propose(AiActionServiceImpl on, String code) {
        try (AiChatTurn turn = AiChatTurns.open(tenantId, userId, sessionId)) {
            ObjectNode payload = JsonNodeFactory.instance
                    .objectNode()
                    .put("when", "2026-12-04")
                    .put("secret", "x");
            return on.propose(new AiToolContext(tenantId, userId, employeeId), code, payload);
        }
    }

    private UUID proposed() {
        UUID id = propose(service, CODE).intentId();
        audit.clear();
        return id;
    }

    private AiActionIntent stored(UUID id) {
        return store.stream().filter(i -> i.getId().equals(id)).findFirst().orElseThrow();
    }

    private void onlyStatusRow(UUID id, AiIntentStatus from, AiIntentStatus to) {
        assertThat(audit).hasSize(1);
        AuditChange row = audit.get(0);
        assertThat(row.operation()).isEqualTo("UPDATE");
        assertThat(row.entitySchema()).isEqualTo("core");
        assertThat(row.entityTable()).isEqualTo("ai_action_intent");
        assertThat(row.entityId()).isEqualTo(id.toString());
        assertThat(row.changedColumns()).containsExactly("status");
        assertThat(row.oldValues()).containsExactly(java.util.Map.entry("status", from.name()));
        assertThat(row.newValues()).containsExactly(java.util.Map.entry("status", to.name()));
    }

    // ---- propose ----

    @Test
    @DisplayName("propose: one PROPOSED intent, 15 minutes, the card from describe; one INSERT audit row, no payload")
    void propose_storesAndAudits() {
        AiProposedAction card = propose(service, CODE);

        AiActionIntent intent = stored(card.intentId());
        assertThat(intent.getStatus()).isEqualTo(AiIntentStatus.PROPOSED);
        assertThat(intent.getExpiresAt()).isEqualTo(base.plus(Duration.ofMinutes(15)));
        assertThat(intent.getSessionId()).isEqualTo(sessionId);
        assertThat(card.summary()).isEqualTo("Do it on 2026-12-04");
        assertThat(card.fields()).containsExactly(new AiActionField("When", "2026-12-04"));
        assertThat(card.expiresAt()).isEqualTo(intent.getExpiresAt());

        assertThat(audit).hasSize(1);
        AuditChange row = audit.get(0);
        assertThat(row.operation()).isEqualTo("INSERT");
        assertThat(row.entityTable()).isEqualTo("ai_action_intent");
        assertThat(row.entityId()).isEqualTo(card.intentId().toString());
        assertThat(row.newValues())
                .containsEntry("action_code", CODE)
                .containsEntry("status", "PROPOSED")
                .containsEntry("session_id", sessionId.toString())
                .containsEntry("expires_at", intent.getExpiresAt().toString())
                .doesNotContainKeys("payload", "result");
        assertThat(row.toString()).doesNotContain("2026-12-04").doesNotContain("secret");
    }

    @Test
    @DisplayName("propose outside a chat message is refused; nothing stored")
    void propose_outsideTurn_refused() {
        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        assertThatThrownBy(() -> service.propose(new AiToolContext(tenantId, userId, employeeId), CODE, payload))
                .isInstanceOf(IllegalStateException.class);
        assertThat(store).isEmpty();
        assertThat(audit).isEmpty();
    }

    // ---- confirm: the re-check table ----

    @Test
    @DisplayName("check 1: another user's intent is not found; nothing runs")
    void wrongUser_notFound() {
        UUID id = proposed();
        signIn(otherUser);

        assertThatThrownBy(() -> service.confirm(id)).isInstanceOf(AiActionNotFoundException.class);
        assertThatThrownBy(() -> service.cancel(id)).isInstanceOf(AiActionNotFoundException.class);
        assertThatThrownBy(() -> service.get(id)).isInstanceOf(AiActionNotFoundException.class);
        assertThat(executor.runs.get()).isZero();
        assertThat(stored(id).getStatus()).isEqualTo(AiIntentStatus.PROPOSED);
        assertThat(audit).isEmpty();
    }

    @Test
    @DisplayName("checks 2 and 9: confirm runs once and stores the result with the trace id; again answers the same")
    void confirm_onceThenSameResult() {
        UUID id = proposed();
        MDC.put(MdcLoggingContext.CORRELATION_ID_KEY, "trace-123");

        AiActionOutcome first = service.confirm(id);
        AiActionOutcome second = service.confirm(id);

        assertThat(first.status()).isEqualTo(AiIntentStatus.CONFIRMED);
        assertThat(first.result().path("done").asText()).isEqualTo("yes");
        assertThat(first.result().path("traceId").asText()).isEqualTo("trace-123");
        assertThat(second).isEqualTo(first);
        assertThat(executor.runs.get()).isEqualTo(1);
        assertThat(executor.lastContext.employeeId()).isEqualTo(employeeId);
        assertThat(stored(id).getConfirmedAt()).isEqualTo(base);
        onlyStatusRow(id, AiIntentStatus.PROPOSED, AiIntentStatus.CONFIRMED);
    }

    @Test
    @DisplayName("C8: the executor is sent the stored payload")
    void confirm_runsStoredPayload() {
        UUID id = proposed();
        service.confirm(id);
        assertThat(executor.lastPayload).isEqualTo(stored(id).getPayload());
    }

    @Test
    @DisplayName("check 3: a cancelled intent answers 409 cancelled; nothing runs")
    void cancelled_refused() {
        UUID id = proposed();
        service.cancel(id);
        audit.clear();

        assertThatThrownBy(() -> service.confirm(id)).isInstanceOf(AiActionCancelledException.class);
        assertThat(executor.runs.get()).isZero();
        assertThat(audit).isEmpty();
    }

    @Test
    @DisplayName("check 4: past its time it becomes EXPIRED (one audit row); a later confirm still runs nothing")
    void expired_marksAndRefuses() {
        UUID id = proposed();
        clock.now = base.plus(Duration.ofMinutes(15));

        assertThatThrownBy(() -> service.confirm(id)).isInstanceOf(AiActionExpiredException.class);
        assertThat(stored(id).getStatus()).isEqualTo(AiIntentStatus.EXPIRED);
        onlyStatusRow(id, AiIntentStatus.PROPOSED, AiIntentStatus.EXPIRED);

        assertThatThrownBy(() -> service.confirm(id)).isInstanceOf(AiActionExpiredException.class);
        assertThat(executor.runs.get()).isZero();
        assertThat(audit).hasSize(1);
    }

    @Test
    @DisplayName("check 4 on read: the first read after its time marks it EXPIRED, once")
    void expired_onRead() {
        UUID id = proposed();
        clock.now = base.plus(Duration.ofMinutes(20));

        AiActionView view = service.get(id);
        service.get(id);

        assertThat(view.status()).isEqualTo(AiIntentStatus.EXPIRED);
        assertThat(view.summary()).isEqualTo("Do it on 2026-12-04");
        onlyStatusRow(id, AiIntentStatus.PROPOSED, AiIntentStatus.EXPIRED);
    }

    @Test
    @DisplayName("check 5: the helper switched off after the proposal: 403 AI_DISABLED; nothing runs; still PROPOSED")
    void helperOff_refused() {
        UUID id = proposed();
        aiOn = false;

        assertThatThrownBy(() -> service.confirm(id)).isInstanceOf(AiDisabledException.class);
        assertThat(executor.runs.get()).isZero();
        assertThat(stored(id).getStatus()).isEqualTo(AiIntentStatus.PROPOSED);
        assertThat(audit).isEmpty();
    }

    @Test
    @DisplayName("check 6: the permission removed after the proposal: 403; nothing runs")
    void permissionRemoved_refused() {
        UUID id = proposed();
        granted.clear();

        assertThatThrownBy(() -> service.confirm(id)).isInstanceOf(AccessDeniedException.class);
        assertThat(executor.runs.get()).isZero();
        assertThat(stored(id).getStatus()).isEqualTo(AiIntentStatus.PROPOSED);
    }

    @Test
    @DisplayName("C7, check 6a: an executor needing HRMS and a customer without it: 403; executor not run")
    void moduleNotHeld_refused() {
        RecordingExecutor hrms = new RecordingExecutor(PlatformModule.HRMS);
        AiActionServiceImpl withHrms = service(hrms);
        UUID id = propose(withHrms, CODE).intentId();

        assertThatThrownBy(() -> withHrms.confirm(id)).isInstanceOf(AccessDeniedException.class);
        assertThat(hrms.runs.get()).isZero();

        licensed.add(PlatformModule.HRMS);
        assertThat(withHrms.confirm(id).status()).isEqualTo(AiIntentStatus.CONFIRMED);
        assertThat(hrms.runs.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("check 7: no employee record for an executor that needs one: 403; nothing runs")
    void noEmployee_refused() {
        UUID id = proposed();
        me = Optional.empty();

        assertThatThrownBy(() -> service.confirm(id)).isInstanceOf(AccessDeniedException.class);
        assertThat(executor.runs.get()).isZero();
    }

    @Test
    @DisplayName(
            "check 8: the executor refuses: FAILED with the reason, 409; one audit row; a later confirm runs nothing")
    void executorRefuses_failed() {
        UUID id = proposed();
        executor.refusal =
                new IllegalStateException("An existing active leave request overlaps with the requested dates");

        assertThatThrownBy(() -> service.confirm(id))
                .isInstanceOf(AiActionFailedException.class)
                .hasMessageContaining("overlaps");
        AiActionIntent intent = stored(id);
        assertThat(intent.getStatus()).isEqualTo(AiIntentStatus.FAILED);
        assertThat(intent.getResult().path("reason").asText()).contains("overlaps");
        onlyStatusRow(id, AiIntentStatus.PROPOSED, AiIntentStatus.FAILED);

        executor.refusal = null;
        assertThatThrownBy(() -> service.confirm(id)).isInstanceOf(AiActionFailedException.class);
        assertThat(executor.runs.get()).isEqualTo(1);
    }

    // ---- cancel ----

    @Test
    @DisplayName(
            "cancel: CANCELLED, one audit row; cancelling twice answers the same; cancelling a confirmed one is 409")
    void cancel_rules() {
        UUID id = proposed();

        AiActionOutcome cancelled = service.cancel(id);
        AiActionOutcome again = service.cancel(id);

        assertThat(cancelled.status()).isEqualTo(AiIntentStatus.CANCELLED);
        assertThat(again).isEqualTo(cancelled);
        onlyStatusRow(id, AiIntentStatus.PROPOSED, AiIntentStatus.CANCELLED);

        UUID other = proposed();
        service.confirm(other);
        assertThatThrownBy(() -> service.cancel(other)).isInstanceOf(AiActionConfirmedException.class);
        assertThat(stored(other).getStatus()).isEqualTo(AiIntentStatus.CONFIRMED);
    }

    @Test
    @DisplayName(
            "C10: propose, confirm, cancel, expire and fail each write exactly one audit row, none with the payload")
    void oneAuditRowPerStep() {
        UUID a = propose(service, CODE).intentId();
        service.confirm(a);
        UUID b = propose(service, CODE).intentId();
        service.cancel(b);
        UUID c = propose(service, CODE).intentId();
        executor.refusal = new IllegalArgumentException("refused");
        assertThatThrownBy(() -> service.confirm(c)).isInstanceOf(AiActionFailedException.class);
        UUID d = propose(service, CODE).intentId();
        clock.now = base.plus(Duration.ofHours(1));
        assertThat(service.get(d).status()).isEqualTo(AiIntentStatus.EXPIRED);

        assertThat(audit)
                .extracting(AuditChange::operation)
                .containsExactly("INSERT", "UPDATE", "INSERT", "UPDATE", "INSERT", "UPDATE", "INSERT", "UPDATE");
        assertThat(audit)
                .extracting(r -> r.newValues().get("status"))
                .containsExactly(
                        "PROPOSED", "CONFIRMED", "PROPOSED", "CANCELLED", "PROPOSED", "FAILED", "PROPOSED", "EXPIRED");
        assertThat(audit).allSatisfy(r -> {
            assertThat(r.newValues()).doesNotContainKeys("payload", "result");
            assertThat(r.toString()).doesNotContain("2026-12-04").doesNotContain("refused");
        });
    }

    // ---- test doubles ----

    /** Answers {@code done: yes}, or throws {@link #refusal}; records what it was sent. */
    private static final class RecordingExecutor implements AiActionExecutor {
        final AtomicInteger runs = new AtomicInteger();
        final PlatformModule module;
        volatile RuntimeException refusal;
        volatile AiToolContext lastContext;
        volatile JsonNode lastPayload;

        RecordingExecutor(PlatformModule module) {
            this.module = module;
        }

        @Override
        public String actionCode() {
            return CODE;
        }

        @Override
        public String requiredAction() {
            return "core.test.do";
        }

        @Override
        public PlatformModule requiredModule() {
            return module;
        }

        @Override
        public AiActionDescription describe(AiToolContext ctx, JsonNode payload) {
            String when = payload.path("when").asText();
            return new AiActionDescription("Do it on " + when, List.of(new AiActionField("When", when)));
        }

        @Override
        public JsonNode execute(AiToolContext ctx, JsonNode payload) {
            runs.incrementAndGet();
            lastContext = ctx;
            lastPayload = payload;
            if (refusal != null) {
                throw refusal;
            }
            return JsonNodeFactory.instance.objectNode().put("done", "yes");
        }
    }

    private static final class MovableClock extends Clock {
        volatile Instant now;

        MovableClock(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
