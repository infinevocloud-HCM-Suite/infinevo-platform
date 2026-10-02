package com.infinevo.payroll.proof;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.approval.ApprovalFlowType;
import com.infinevo.core.approval.ApprovalService;
import com.infinevo.core.approval.SubjectRef;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for {@link ProofApprovalStarter} (W-34.2 spec §3 and §4).
 */
class ProofApprovalStarterTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID PROOF_ID = UUID.randomUUID();
    private static final UUID EMPLOYEE_ID = UUID.randomUUID();
    private static final String FY = "2026-2027";

    private ApprovalService approvalService;
    private EmployeeProofOfInvestmentRepository proofRepository;
    private EmployeeProofItemRepository itemRepository;
    private ProofApprovalStarter starter;

    @BeforeEach
    void setUp() {
        approvalService = mock(ApprovalService.class);
        proofRepository = mock(EmployeeProofOfInvestmentRepository.class);
        itemRepository = mock(EmployeeProofItemRepository.class);
        starter = new ProofApprovalStarter(approvalService, proofRepository, itemRepository);
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("onProofSubmitted starts approval instance with claimed items and saves instanceId on proof")
    void onProofSubmittedStartsApprovalInstance() {
        EmployeeProofOfInvestment proof =
                new EmployeeProofOfInvestment(TENANT_ID, EMPLOYEE_ID, UUID.randomUUID(), FY, "employee");

        UUID item1Id = UUID.randomUUID();
        UUID item2Id = UUID.randomUUID();
        UUID item3Id = UUID.randomUUID();
        UUID item4Id = UUID.randomUUID();

        EmployeeProofItem item1 = new EmployeeProofItem(
                TENANT_ID,
                PROOF_ID,
                ProofSourceKind.HOUSE_RENT,
                UUID.randomUUID(),
                "Rent",
                new BigDecimal("100000"),
                "employee");
        item1.setClaimedAmount(new BigDecimal("50000"));

        EmployeeProofItem item2 = new EmployeeProofItem(
                TENANT_ID,
                PROOF_ID,
                ProofSourceKind.SECTION_6A,
                UUID.randomUUID(),
                "PPF",
                new BigDecimal("50000"),
                "employee");
        item2.setClaimedAmount(BigDecimal.ZERO);

        EmployeeProofItem item3 = new EmployeeProofItem(
                TENANT_ID,
                PROOF_ID,
                ProofSourceKind.SECTION_6A,
                UUID.randomUUID(),
                "ELSS",
                new BigDecimal("50000"),
                "employee");
        item3.setClaimedAmount(null);

        EmployeeProofItem item4 = new EmployeeProofItem(
                TENANT_ID,
                PROOF_ID,
                ProofSourceKind.HOME_LOAN_INTEREST,
                UUID.randomUUID(),
                "Interest",
                new BigDecimal("200000"),
                "employee");
        item4.setClaimedAmount(new BigDecimal("150000"));

        // Use reflection or package-private helper if needed to set id on items
        setId(item1, item1Id);
        setId(item2, item2Id);
        setId(item3, item3Id);
        setId(item4, item4Id);

        when(proofRepository.findByTenantIdAndId(TENANT_ID, PROOF_ID)).thenReturn(Optional.of(proof));
        when(itemRepository.findByTenantIdAndProofIdOrderByCreatedAtAscIdAsc(TENANT_ID, PROOF_ID))
                .thenReturn(List.of(item1, item2, item3, item4));

        UUID expectedInstanceId = UUID.randomUUID();
        when(approvalService.start(eq(ApprovalFlowType.PROOF_OF_INVESTMENT), any(), eq(EMPLOYEE_ID), any()))
                .thenReturn(expectedInstanceId);

        ProofSubmittedEvent event = new ProofSubmittedEvent(TENANT_ID, PROOF_ID, EMPLOYEE_ID, FY);
        starter.onProofSubmitted(event);

        ArgumentCaptor<SubjectRef> subjectCaptor = ArgumentCaptor.forClass(SubjectRef.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> itemRefsCaptor = ArgumentCaptor.forClass(List.class);

        verify(approvalService)
                .start(
                        eq(ApprovalFlowType.PROOF_OF_INVESTMENT),
                        subjectCaptor.capture(),
                        eq(EMPLOYEE_ID),
                        itemRefsCaptor.capture());

        SubjectRef subject = subjectCaptor.getValue();
        assertThat(subject.table()).isEqualTo("payroll.employee_proof_of_investment");
        assertThat(subject.id()).isEqualTo(PROOF_ID);

        List<String> itemRefs = itemRefsCaptor.getValue();
        assertThat(itemRefs).containsExactly(item1Id.toString(), item4Id.toString());

        assertThat(proof.getApprovalInstanceId()).isEqualTo(expectedInstanceId);
        verify(proofRepository).save(proof);
    }

    @Test
    @DisplayName("onProofSubmitted throws IllegalStateException when proof is not found")
    void onProofSubmittedThrowsWhenProofNotFound() {
        when(proofRepository.findByTenantIdAndId(TENANT_ID, PROOF_ID)).thenReturn(Optional.empty());

        ProofSubmittedEvent event = new ProofSubmittedEvent(TENANT_ID, PROOF_ID, EMPLOYEE_ID, FY);

        assertThatThrownBy(() -> starter.onProofSubmitted(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Proof of investment not found");
    }

    private static void setId(Object entity, UUID id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
