package com.infinevo.core.ai.action;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.core.ai.AiAccessGate;
import com.infinevo.core.ai.AiActionIntent;
import com.infinevo.core.ai.AiActionIntentRepository;
import com.infinevo.core.ai.AiChatTurn;
import com.infinevo.core.ai.AiChatTurns;
import com.infinevo.core.ai.AiToolContext;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.shared.audit.AuditWriter;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.entitlement.EntitlementService;
import com.infinevo.shared.tenant.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionOperations;

/**
 * W-114.28 unit tests: a real {@link AiActionServiceImpl} over an in-memory intent store, so "one proposal per
 * message" and the stored payload are the real ones. Open in a test's set-up, close in its tear-down.
 */
final class AdminStepsFixture implements AutoCloseable {

    static final Pattern UUID_TEXT = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    final UUID tenantId = UUID.randomUUID();
    final UUID userId = UUID.randomUUID();
    final List<AiActionIntent> stored = new ArrayList<>();
    final AiToolContext ctx;
    final AiActionServiceImpl actions;
    private AiChatTurn turn;

    AdminStepsFixture(List<AiActionExecutor> executors) {
        TenantContext.set(tenantId);
        AiActionIntentRepository intents = mock(AiActionIntentRepository.class);
        when(intents.save(any(AiActionIntent.class))).thenAnswer(inv -> {
            AiActionIntent i = inv.getArgument(0);
            if (i.getId() == null) {
                ReflectionTestUtils.setField(i, "id", UUID.randomUUID());
                stored.add(i);
            }
            return i;
        });
        actions = new AiActionServiceImpl(
                intents,
                executors,
                mock(AiAccessGate.class),
                mock(PermissionService.class),
                mock(EntitlementService.class),
                mock(EmployeeService.class),
                mock(AuditWriter.class),
                TransactionOperations.withoutTransaction(),
                Clock.fixed(Instant.parse("2026-11-27T09:00:00Z"), ZoneOffset.UTC));
        ctx = new AiToolContext(tenantId, userId, null);
        turn = AiChatTurns.open(tenantId, userId, UUID.randomUUID());
    }

    /** A new user message: the one-proposal limit starts again. */
    void nextMessage() {
        turn.close();
        turn = AiChatTurns.open(tenantId, userId, UUID.randomUUID());
    }

    @Override
    public void close() {
        turn.close();
        TenantContext.clear();
    }
}
