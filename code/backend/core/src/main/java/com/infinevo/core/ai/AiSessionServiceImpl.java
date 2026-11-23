package com.infinevo.core.ai;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.shared.tenant.TenantContext;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation of {@link AiSessionService} (W-114.3).
 *
 * <p>Every lookup is by id <em>and</em> tenant <em>and</em> user, so another user's chat in the same tenant is "not
 * found"; row-level security keeps another tenant's out besides. The user is the token subject ({@link AiCaller}).
 */
@Service
public class AiSessionServiceImpl implements AiSessionService {

    private final AiSessionRepository sessions;
    private final AiMessageRepository messages;
    private final AiActionIntentRepository intents;
    private final EmployeeService employeeService;

    public AiSessionServiceImpl(
            AiSessionRepository sessions,
            AiMessageRepository messages,
            AiActionIntentRepository intents,
            EmployeeService employeeService) {
        this.sessions = Objects.requireNonNull(sessions, "sessions must not be null");
        this.messages = Objects.requireNonNull(messages, "messages must not be null");
        this.intents = Objects.requireNonNull(intents, "intents must not be null");
        this.employeeService = Objects.requireNonNull(employeeService, "employeeService must not be null");
    }

    @Override
    @Transactional
    public AiSessionResponse create(String title) {
        UUID tenantId = TenantContext.require();
        UUID userId = requireUser();
        String cleanTitle = cleanTitle(title);
        UUID employeeId =
                employeeService.currentEmployee().map(EmployeeResponse::id).orElse(null);
        AiSession saved = sessions.save(new AiSession(tenantId, userId, employeeId, cleanTitle));
        return AiSessionResponse.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AiSessionResponse> listMine(Pageable pageable) {
        UUID tenantId = TenantContext.require();
        UUID userId = requireUser();
        return sessions.findByTenantIdAndUserIdOrderByLastMessageAtDesc(tenantId, userId, pageable)
                .map(AiSessionResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AiMessageResponse> messages(UUID sessionId) {
        AiSession session = requireMine(sessionId);
        List<AiMessage> visible =
                messages.findByTenantIdAndSessionIdOrderByCreatedAtAsc(session.getTenantId(), session.getId()).stream()
                        .filter(m -> m.getRole() != AiMessageRole.TOOL)
                        .toList();
        if (visible.isEmpty()) {
            return List.of();
        }
        Map<UUID, UUID> intentByMessage = intents
                .findByTenantIdAndMessageIdIn(
                        session.getTenantId(),
                        visible.stream().map(AiMessage::getId).toList())
                .stream()
                .filter(i -> i.getMessageId() != null && session.getId().equals(i.getSessionId()))
                .collect(Collectors.toMap(
                        AiActionIntent::getMessageId, AiActionIntent::getId, (first, second) -> first));
        Function<AiMessage, AiMessageResponse.ProposedAction> proposed = m -> {
            UUID intentId = m.getRole() == AiMessageRole.ASSISTANT ? intentByMessage.get(m.getId()) : null;
            return intentId == null ? null : new AiMessageResponse.ProposedAction(intentId);
        };
        return visible.stream()
                .map(m -> new AiMessageResponse(
                        m.getId(), m.getRole(), m.getContent(), m.getCreatedAt(), proposed.apply(m)))
                .toList();
    }

    @Override
    @Transactional
    public void delete(UUID sessionId) {
        sessions.delete(requireMine(sessionId));
    }

    private AiSession requireMine(UUID sessionId) {
        if (sessionId == null) {
            throw new IllegalArgumentException("session id must be provided");
        }
        UUID tenantId = TenantContext.require();
        UUID userId = requireUser();
        return sessions.findByIdAndTenantIdAndUserId(sessionId, tenantId, userId)
                .orElseThrow(() -> new AiSessionNotFoundException(sessionId));
    }

    /** {@code @RequiresAction} has already refused a caller with no user; this is the same refusal, kept closed. */
    private static UUID requireUser() {
        return AiCaller.currentUserId()
                .orElseThrow(() -> new AccessDeniedException("No signed-in user with a UUID subject"));
    }

    private static String cleanTitle(String title) {
        if (title == null || title.isBlank()) {
            return null;
        }
        String trimmed = title.strip();
        if (trimmed.length() > AiSession.MAX_TITLE) {
            throw new IllegalArgumentException("title must be at most " + AiSession.MAX_TITLE + " characters");
        }
        return trimmed;
    }
}
