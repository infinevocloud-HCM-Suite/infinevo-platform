package com.infinevo.payroll.proof;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.approval.ApprovalFlowType;
import com.infinevo.core.approval.ApprovalInstance;
import com.infinevo.core.approval.ApprovalInstanceRepository;
import com.infinevo.core.approval.StepDecision;
import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.notification.NotificationEvent;
import com.infinevo.core.notification.NotificationService;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ProofOutcomeHandlerTest {

    private ApprovalInstanceRepository instanceRepository;
    private EmployeeProofOfInvestmentRepository proofRepository;
    private EmployeeProofItemRepository itemRepository;
    private EmployeeService employeeService;
    private NotificationService notificationService;
    private Clock clock;
    private ProofOutcomeHandler handler;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID instanceId = UUID.randomUUID();
    private final UUID proofId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();
    private final UUID declarationId = UUID.randomUUID();
    private final Instant now = Instant.parse("2026-10-01T10:00:00Z");

    @BeforeEach
    void setUp() {
        instanceRepository = mock(ApprovalInstanceRepository.class);
        proofRepository = mock(EmployeeProofOfInvestmentRepository.class);
        itemRepository = mock(EmployeeProofItemRepository.class);
        employeeService = mock(EmployeeService.class);
        notificationService = mock(NotificationService.class);
        clock = Clock.fixed(now, ZoneId.of("UTC"));

        handler = new ProofOutcomeHandler(
                instanceRepository, proofRepository, itemRepository, employeeService, notificationService, clock);

        EmployeeResponse mockEmp = mock(EmployeeResponse.class);
        when(mockEmp.firstName()).thenReturn("Alice");
        when(mockEmp.lastName()).thenReturn("Smith");
        when(employeeService.get(employeeId)).thenReturn(mockEmp);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("flowType returns PROOF_OF_INVESTMENT")
    void flowType_returnsProofOfInvestment() {
        assertThat(handler.flowType()).isEqualTo(ApprovalFlowType.PROOF_OF_INVESTMENT);
    }

    @Test
    @DisplayName("onApproved marks proof APPROVED, sets decidedAt, updates items, and sends notification")
    void onApproved_happyPath() {
        ApprovalInstance instance = createInstance();
        when(instanceRepository.findById(instanceId)).thenReturn(Optional.of(instance));

        EmployeeProofOfInvestment proof = createProof(ProofStatus.SUBMITTED, instanceId);
        when(proofRepository.findByTenantIdAndId(tenantId, proofId)).thenReturn(Optional.of(proof));

        UUID item1Id = UUID.randomUUID();
        UUID item2Id = UUID.randomUUID();
        EmployeeProofItem item1 = createItem(
                item1Id, ProofSourceKind.SECTION_6A, new BigDecimal("100000.0000"), null, ProofItemStatus.PENDING);
        EmployeeProofItem item2 = createItem(
                item2Id, ProofSourceKind.HOUSE_RENT, new BigDecimal("50000.0000"), null, ProofItemStatus.PENDING);
        when(itemRepository.findByTenantIdAndProofIdOrderByCreatedAtAscIdAsc(tenantId, proofId))
                .thenReturn(List.of(item1, item2));

        StepDecision d1 = new StepDecision(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "APPROVED",
                "Looks good",
                new BigDecimal("80000.0000"),
                item1Id.toString());
        StepDecision d2 = new StepDecision(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "APPROVED",
                "Disallowed rent",
                BigDecimal.ZERO,
                item2Id.toString());

        handler.onApproved(instanceId, List.of(d1, d2));

        assertThat(proof.getStatus()).isEqualTo(ProofStatus.APPROVED);
        assertThat(proof.getDecidedAt()).isEqualTo(now);
        verify(proofRepository).save(proof);

        assertThat(item1.getStatus()).isEqualTo(ProofItemStatus.APPROVED);
        assertThat(item1.getApprovedAmount()).isEqualByComparingTo(new BigDecimal("80000.0000"));
        assertThat(item1.getReviewerNote()).isEqualTo("Looks good");

        assertThat(item2.getStatus()).isEqualTo(ProofItemStatus.DISALLOWED);
        assertThat(item2.getApprovedAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(item2.getReviewerNote()).isEqualTo("Disallowed rent");

        verify(notificationService)
                .compose(
                        eq(NotificationEvent.APPROVAL_DECIDED),
                        eq(employeeId),
                        eq(Map.of(
                                "employee_name", "Alice Smith",
                                "request_title", "Proof of Investment 2026-2027",
                                "decision", "Approved")));
    }

    @Test
    @DisplayName("onApproved never approves a claimed amount no step decided: it allows nothing")
    void onApproved_undecidedClaimIsNotApproved() {
        ApprovalInstance instance = createInstance();
        when(instanceRepository.findById(instanceId)).thenReturn(Optional.of(instance));
        EmployeeProofOfInvestment proof = createProof(ProofStatus.SUBMITTED, instanceId);
        when(proofRepository.findByTenantIdAndId(tenantId, proofId)).thenReturn(Optional.of(proof));

        UUID decidedId = UUID.randomUUID();
        UUID undecidedId = UUID.randomUUID();
        EmployeeProofItem decided = createItem(
                decidedId, ProofSourceKind.SECTION_6A, new BigDecimal("1000.0000"), null, ProofItemStatus.PENDING);
        EmployeeProofItem undecided = createItem(
                undecidedId, ProofSourceKind.HOUSE_RENT, new BigDecimal("90000.0000"), null, ProofItemStatus.PENDING);
        when(itemRepository.findByTenantIdAndProofIdOrderByCreatedAtAscIdAsc(tenantId, proofId))
                .thenReturn(List.of(decided, undecided));
        StepDecision onlyDecision = new StepDecision(
                UUID.randomUUID(), UUID.randomUUID(), "APPROVED", "ok", new BigDecimal("1000"), decidedId.toString());

        handler.onApproved(instanceId, List.of(onlyDecision));

        assertThat(decided.getStatus()).isEqualTo(ProofItemStatus.APPROVED);
        assertThat(undecided.getStatus()).isEqualTo(ProofItemStatus.DISALLOWED);
        assertThat(undecided.getApprovedAmount())
                .as("a claim nobody reviewed must not reduce anyone's tax")
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("onApproved clamps approved amount if decision exceeded claimed amount")
    void onApproved_clampsAboveClaimed() {
        ApprovalInstance instance = createInstance();
        when(instanceRepository.findById(instanceId)).thenReturn(Optional.of(instance));

        EmployeeProofOfInvestment proof = createProof(ProofStatus.SUBMITTED, instanceId);
        when(proofRepository.findByTenantIdAndId(tenantId, proofId)).thenReturn(Optional.of(proof));

        UUID item1Id = UUID.randomUUID();
        EmployeeProofItem item1 = createItem(
                item1Id, ProofSourceKind.SECTION_6A, new BigDecimal("50000.0000"), null, ProofItemStatus.PENDING);
        when(itemRepository.findByTenantIdAndProofIdOrderByCreatedAtAscIdAsc(tenantId, proofId))
                .thenReturn(List.of(item1));

        StepDecision d1 = new StepDecision(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "APPROVED",
                "Over limit",
                new BigDecimal("60000.0000"),
                item1Id.toString());

        handler.onApproved(instanceId, List.of(d1));

        assertThat(item1.getStatus()).isEqualTo(ProofItemStatus.APPROVED);
        assertThat(item1.getApprovedAmount()).isEqualByComparingTo(new BigDecimal("50000.0000"));
    }

    @Test
    @DisplayName("onApproved is idempotent when proof is already APPROVED")
    void onApproved_idempotentWhenAlreadyApproved() {
        ApprovalInstance instance = createInstance();
        when(instanceRepository.findById(instanceId)).thenReturn(Optional.of(instance));

        EmployeeProofOfInvestment proof = createProof(ProofStatus.APPROVED, instanceId);
        when(proofRepository.findByTenantIdAndId(tenantId, proofId)).thenReturn(Optional.of(proof));

        handler.onApproved(instanceId, List.of());

        verify(proofRepository, never()).save(any());
        verify(notificationService, never()).compose(any(), any(), any());
    }

    @Test
    @DisplayName("onApproved is a no-op when instanceId does not match proof's approvalInstanceId")
    void onApproved_instanceMismatch_noop() {
        ApprovalInstance instance = createInstance();
        when(instanceRepository.findById(instanceId)).thenReturn(Optional.of(instance));

        EmployeeProofOfInvestment proof = createProof(ProofStatus.SUBMITTED, UUID.randomUUID());
        when(proofRepository.findByTenantIdAndId(tenantId, proofId)).thenReturn(Optional.of(proof));

        handler.onApproved(instanceId, List.of());

        verify(proofRepository, never()).save(any());
        verify(notificationService, never()).compose(any(), any(), any());
    }

    @Test
    @DisplayName("onRejected preserves RETURNED item status and reason, resets others to PENDING with null amount")
    void onRejected_withReturnedItem() {
        ApprovalInstance instance = createInstance();
        when(instanceRepository.findById(instanceId)).thenReturn(Optional.of(instance));

        EmployeeProofOfInvestment proof = createProof(ProofStatus.SUBMITTED, instanceId);
        when(proofRepository.findByTenantIdAndId(tenantId, proofId)).thenReturn(Optional.of(proof));

        UUID item1Id = UUID.randomUUID();
        UUID item2Id = UUID.randomUUID();
        EmployeeProofItem item1 = createItem(
                item1Id,
                ProofSourceKind.SECTION_6A,
                new BigDecimal("100000.0000"),
                "Receipt unclear",
                ProofItemStatus.RETURNED);
        EmployeeProofItem item2 = createItem(
                item2Id, ProofSourceKind.HOUSE_RENT, new BigDecimal("50000.0000"), null, ProofItemStatus.PENDING);
        ReflectionTestUtils.setField(item2, "approvedAmount", new BigDecimal("40000.0000"));

        when(itemRepository.findByTenantIdAndProofIdOrderByCreatedAtAscIdAsc(tenantId, proofId))
                .thenReturn(List.of(item1, item2));

        handler.onRejected(instanceId, List.of());

        assertThat(proof.getStatus()).isEqualTo(ProofStatus.REJECTED);
        assertThat(proof.getDecidedAt()).isEqualTo(now);
        verify(proofRepository).save(proof);

        assertThat(item1.getStatus()).isEqualTo(ProofItemStatus.RETURNED);
        assertThat(item1.getReviewerNote()).isEqualTo("Receipt unclear");

        assertThat(item2.getStatus()).isEqualTo(ProofItemStatus.PENDING);
        assertThat(item2.getApprovedAmount()).isNull();

        verify(notificationService)
                .compose(
                        eq(NotificationEvent.APPROVAL_DECIDED),
                        eq(employeeId),
                        eq(Map.of(
                                "employee_name", "Alice Smith",
                                "request_title", "Proof of Investment 2026-2027",
                                "decision", "Returned")));
    }

    @Test
    @DisplayName("onRejected without RETURNED item copies REJECTED step comment to proof reviewerNote")
    void onRejected_withoutReturnedItem_copiesStepComment() {
        ApprovalInstance instance = createInstance();
        when(instanceRepository.findById(instanceId)).thenReturn(Optional.of(instance));

        EmployeeProofOfInvestment proof = createProof(ProofStatus.SUBMITTED, instanceId);
        when(proofRepository.findByTenantIdAndId(tenantId, proofId)).thenReturn(Optional.of(proof));

        UUID item1Id = UUID.randomUUID();
        EmployeeProofItem item1 = createItem(
                item1Id, ProofSourceKind.SECTION_6A, new BigDecimal("100000.0000"), null, ProofItemStatus.PENDING);
        when(itemRepository.findByTenantIdAndProofIdOrderByCreatedAtAscIdAsc(tenantId, proofId))
                .thenReturn(List.of(item1));

        StepDecision rejectDecision =
                new StepDecision(UUID.randomUUID(), UUID.randomUUID(), "REJECTED", "Global return reason", null, null);

        handler.onRejected(instanceId, List.of(rejectDecision));

        assertThat(proof.getStatus()).isEqualTo(ProofStatus.REJECTED);
        assertThat(proof.getReviewerNote()).isEqualTo("Global return reason");
        verify(proofRepository).save(proof);
    }

    @Test
    @DisplayName("onRejected is idempotent when proof is already REJECTED")
    void onRejected_idempotentWhenAlreadyRejected() {
        ApprovalInstance instance = createInstance();
        when(instanceRepository.findById(instanceId)).thenReturn(Optional.of(instance));

        EmployeeProofOfInvestment proof = createProof(ProofStatus.REJECTED, instanceId);
        when(proofRepository.findByTenantIdAndId(tenantId, proofId)).thenReturn(Optional.of(proof));

        handler.onRejected(instanceId, List.of());

        verify(proofRepository, never()).save(any());
        verify(notificationService, never()).compose(any(), any(), any());
    }

    private ApprovalInstance createInstance() {
        ApprovalInstance instance = mock(ApprovalInstance.class);
        when(instance.getId()).thenReturn(instanceId);
        when(instance.getTenantId()).thenReturn(tenantId);
        when(instance.getSubjectId()).thenReturn(proofId);
        return instance;
    }

    private EmployeeProofOfInvestment createProof(ProofStatus status, UUID appInstanceId) {
        EmployeeProofOfInvestment proof =
                new EmployeeProofOfInvestment(tenantId, employeeId, declarationId, "2026-2027", "actor");
        ReflectionTestUtils.setField(proof, "id", proofId);
        ReflectionTestUtils.setField(proof, "status", status);
        ReflectionTestUtils.setField(proof, "approvalInstanceId", appInstanceId);
        return proof;
    }

    private EmployeeProofItem createItem(
            UUID itemId, ProofSourceKind kind, BigDecimal claimedAmount, String reviewerNote, ProofItemStatus status) {
        EmployeeProofItem item =
                new EmployeeProofItem(tenantId, proofId, kind, UUID.randomUUID(), "Test item", claimedAmount, "actor");
        ReflectionTestUtils.setField(item, "id", itemId);
        ReflectionTestUtils.setField(item, "claimedAmount", claimedAmount);
        ReflectionTestUtils.setField(item, "reviewerNote", reviewerNote);
        ReflectionTestUtils.setField(item, "status", status);
        return item;
    }
}
