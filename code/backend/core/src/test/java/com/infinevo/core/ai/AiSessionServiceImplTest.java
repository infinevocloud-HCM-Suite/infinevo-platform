package com.infinevo.core.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.employee.EmploymentStatus;
import com.infinevo.shared.tenant.TenantContext;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * W-114.3 spec, Tests: {@link AiSessionServiceImpl} over in-memory repositories, so "only the caller's" is what the
 * stored rows give back for the caller's tenant and user, not a mock's say-so.
 */
class AiSessionServiceImplTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID userA = UUID.randomUUID();
    private final UUID userB = UUID.randomUUID();
    private final UUID employeeA = UUID.randomUUID();

    private final List<AiSession> sessionStore = new ArrayList<>();
    private final List<AiMessage> messageStore = new ArrayList<>();
    private final List<AiActionIntent> intentStore = new ArrayList<>();

    private AiSessionRepository sessions;
    private EmployeeService employeeService;
    private AiSessionServiceImpl service;

    @BeforeEach
    void setUp() {
        TenantContext.set(tenantId);

        sessions = mock(AiSessionRepository.class);
        when(sessions.save(any(AiSession.class))).thenAnswer(inv -> {
            AiSession s = inv.getArgument(0);
            if (s.getId() == null) {
                ReflectionTestUtils.setField(s, "id", UUID.randomUUID());
            }
            s.onCreate();
            sessionStore.add(s);
            return s;
        });
        when(sessions.findByIdAndTenantIdAndUserId(any(), any(), any())).thenAnswer(inv -> sessionStore.stream()
                .filter(s -> s.getId().equals(inv.getArgument(0))
                        && s.getTenantId().equals(inv.getArgument(1))
                        && s.getUserId().equals(inv.getArgument(2)))
                .findFirst());
        when(sessions.findByTenantIdAndUserIdOrderByLastMessageAtDesc(any(), any(), any()))
                .thenAnswer(inv -> {
                    List<AiSession> mine = sessionStore.stream()
                            .filter(s -> s.getTenantId().equals(inv.getArgument(0))
                                    && s.getUserId().equals(inv.getArgument(1)))
                            .toList();
                    return new PageImpl<>(mine, inv.<Pageable>getArgument(2), mine.size());
                });

        AiMessageRepository messages = mock(AiMessageRepository.class);
        when(messages.findByTenantIdAndSessionIdOrderByCreatedAtAsc(any(), any()))
                .thenAnswer(inv -> messageStore.stream()
                        .filter(m -> m.getTenantId().equals(inv.getArgument(0))
                                && m.getSessionId().equals(inv.getArgument(1)))
                        .sorted(Comparator.comparing(AiMessage::getCreatedAt))
                        .toList());

        AiActionIntentRepository intents = mock(AiActionIntentRepository.class);
        when(intents.findByTenantIdAndMessageIdIn(any(), anyCollection())).thenAnswer(inv -> {
            Collection<UUID> ids = inv.getArgument(1);
            return intentStore.stream()
                    .filter(i -> i.getTenantId().equals(inv.getArgument(0)) && ids.contains(i.getMessageId()))
                    .toList();
        });

        employeeService = mock(EmployeeService.class);
        when(employeeService.currentEmployee()).thenReturn(Optional.empty());

        service = new AiSessionServiceImpl(sessions, messages, intents, employeeService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("create fills the tenant, the token subject as user and the caller's employee record")
    void create_fillsUserAndEmployee() {
        signIn(userA);
        when(employeeService.currentEmployee()).thenReturn(Optional.of(employee(employeeA)));

        AiSessionResponse created = service.create("  Leave questions  ");

        assertThat(created.id()).isNotNull();
        assertThat(created.title()).isEqualTo("Leave questions");
        assertThat(created.createdAt()).isNotNull();
        assertThat(created.lastMessageAt()).isNull();
        AiSession stored = sessionStore.get(0);
        assertThat(stored.getTenantId()).isEqualTo(tenantId);
        assertThat(stored.getUserId()).isEqualTo(userA);
        assertThat(stored.getEmployeeId()).isEqualTo(employeeA);
    }

    @Test
    @DisplayName("a user with no employee record still creates a chat, with no employee id; a blank title is none")
    void create_noEmployee() {
        signIn(userA);

        AiSessionResponse created = service.create("   ");

        assertThat(created.id()).isNotNull();
        assertThat(created.title()).isNull();
        assertThat(sessionStore.get(0).getEmployeeId()).isNull();
        assertThat(sessionStore.get(0).getUserId()).isEqualTo(userA);
    }

    @Test
    @DisplayName("a title over 200 characters is refused and nothing is stored")
    void create_titleTooLong() {
        signIn(userA);

        assertThatThrownBy(() -> service.create("x".repeat(201))).isInstanceOf(IllegalArgumentException.class);
        assertThat(sessionStore).isEmpty();
    }

    @Test
    @DisplayName("listMine returns only the caller's chats, not another user's in the same tenant")
    void listMine_onlyCallers() {
        UUID mine = stored(userA, tenantId);
        stored(userB, tenantId);
        stored(userA, UUID.randomUUID());
        signIn(userA);

        Page<AiSessionResponse> page = service.listMine(PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(AiSessionResponse::id).containsExactly(mine);
    }

    @Test
    @DisplayName("messages: USER and ASSISTANT oldest first, TOOL rows hidden; proposedAction from the intent")
    void messages_hidesToolRows() {
        UUID session = stored(userA, tenantId);
        Instant t0 = Instant.parse("2026-11-20T10:00:00Z");
        UUID question = message(session, AiMessageRole.USER, "How many days of leave do I have?", t0);
        message(session, AiMessageRole.TOOL, "{\"balance\":12}", t0.plusSeconds(1));
        UUID answer = message(session, AiMessageRole.ASSISTANT, "You have 12 days.", t0.plusSeconds(2));
        UUID intentId = intent(session, userA, answer);
        signIn(userA);

        List<AiMessageResponse> listed = service.messages(session);

        assertThat(listed).extracting(AiMessageResponse::id).containsExactly(question, answer);
        assertThat(listed)
                .extracting(AiMessageResponse::role)
                .containsExactly(AiMessageRole.USER, AiMessageRole.ASSISTANT);
        assertThat(listed.get(0).proposedAction()).isNull();
        assertThat(listed.get(1).proposedAction()).isEqualTo(new AiMessageResponse.ProposedAction(intentId));
    }

    @Test
    @DisplayName("messages of another user's chat in the same tenant: not found")
    void messages_otherUsersSession_notFound() {
        UUID theirs = stored(userB, tenantId);
        message(theirs, AiMessageRole.USER, "B's question", Instant.now());
        signIn(userA);

        assertThatThrownBy(() -> service.messages(theirs)).isInstanceOf(AiSessionNotFoundException.class);
    }

    @Test
    @DisplayName("delete of another user's chat throws not found and deletes nothing; delete of one's own deletes it")
    void delete_otherUsersSession_notFound() {
        UUID theirs = stored(userB, tenantId);
        UUID mine = stored(userA, tenantId);
        signIn(userA);

        assertThatThrownBy(() -> service.delete(theirs)).isInstanceOf(AiSessionNotFoundException.class);
        verify(sessions, never()).delete(any(AiSession.class));

        service.delete(mine);
        verify(sessions)
                .delete(sessionStore.stream()
                        .filter(s -> s.getId().equals(mine))
                        .findFirst()
                        .orElseThrow());
    }

    // ---- helpers ----

    private void signIn(UUID userId) {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(userId.toString(), null, List.of()));
    }

    private UUID stored(UUID userId, UUID tenant) {
        AiSession s = new AiSession(tenant, userId, null, null);
        ReflectionTestUtils.setField(s, "id", UUID.randomUUID());
        s.onCreate();
        sessionStore.add(s);
        return s.getId();
    }

    private UUID message(UUID sessionId, AiMessageRole role, String content, Instant at) {
        AiMessage m = new AiMessage(tenantId, sessionId, role, content);
        ReflectionTestUtils.setField(m, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(m, "createdAt", at);
        messageStore.add(m);
        return m.getId();
    }

    private UUID intent(UUID sessionId, UUID userId, UUID messageId) {
        AiActionIntent i = new AiActionIntent(
                tenantId,
                sessionId,
                userId,
                messageId,
                "core.leave.apply",
                JsonNodeFactory.instance.objectNode(),
                Instant.now().plusSeconds(600));
        ReflectionTestUtils.setField(i, "id", UUID.randomUUID());
        intentStore.add(i);
        return i.getId();
    }

    private EmployeeResponse employee(UUID id) {
        return new EmployeeResponse(
                id,
                tenantId,
                "E-1",
                "First",
                null,
                "Last",
                null,
                LocalDate.of(2024, 1, 1),
                null,
                EmploymentStatus.ACTIVE,
                null,
                null,
                true,
                null,
                null,
                null,
                null,
                null,
                null);
    }
}
