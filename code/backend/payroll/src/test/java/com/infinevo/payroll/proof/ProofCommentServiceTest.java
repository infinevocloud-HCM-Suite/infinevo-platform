package com.infinevo.payroll.proof;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.shared.tenant.TenantContext;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for {@link ProofCommentServiceImpl} (W-34.2 spec §4).
 */
class ProofCommentServiceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();

    private EmployeeProofOfInvestmentRepository proofRepository;
    private EmployeeProofItemRepository itemRepository;
    private EmployeeProofItemCommentRepository commentRepository;
    private EmployeeService employeeService;

    private ProofCommentServiceImpl commentService;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT_ID);

        proofRepository = mock(EmployeeProofOfInvestmentRepository.class);
        itemRepository = mock(EmployeeProofItemRepository.class);
        commentRepository = mock(EmployeeProofItemCommentRepository.class);
        employeeService = mock(EmployeeService.class);

        commentService =
                new ProofCommentServiceImpl(proofRepository, itemRepository, commentRepository, employeeService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static EmployeeResponse stubEmployee(UUID id, String email) {
        EmployeeResponse emp = mock(EmployeeResponse.class);
        given(emp.id()).willReturn(id);
        given(emp.workEmail()).willReturn(email);
        return emp;
    }

    @Test
    @DisplayName("listForReviewer: returns oldest-first comments when proof and item exist")
    void listForReviewerSuccess() {
        UUID proofId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();

        EmployeeProofOfInvestment proof =
                new EmployeeProofOfInvestment(TENANT_ID, UUID.randomUUID(), UUID.randomUUID(), "2026-2027", "test");
        EmployeeProofItem item = new EmployeeProofItem(
                TENANT_ID,
                proofId,
                ProofSourceKind.SECTION_6A,
                UUID.randomUUID(),
                "PPF",
                java.math.BigDecimal.TEN,
                "test");

        EmployeeProofItemComment comment = new EmployeeProofItemComment(
                TENANT_ID, itemId, UUID.randomUUID(), ProofCommentRole.REVIEWER, "Please verify receipt", "test");
        ReflectionTestUtils.setField(comment, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(comment, "createdAt", Instant.now());

        given(proofRepository.findByTenantIdAndId(TENANT_ID, proofId)).willReturn(Optional.of(proof));
        given(itemRepository.findByTenantIdAndProofIdAndId(TENANT_ID, proofId, itemId))
                .willReturn(Optional.of(item));
        given(commentRepository.findByTenantIdAndItemIdOrderByCreatedAtAscIdAsc(TENANT_ID, itemId))
                .willReturn(List.of(comment));

        List<ProofCommentResponse> result = commentService.listForReviewer(proofId, itemId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).body()).isEqualTo("Please verify receipt");
        assertThat(result.get(0).authorRole()).isEqualTo(ProofCommentRole.REVIEWER);
    }

    @Test
    @DisplayName("listForReviewer: throws 404 when proof does not exist")
    void listForReviewerProofNotFound() {
        UUID proofId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();

        given(proofRepository.findByTenantIdAndId(TENANT_ID, proofId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.listForReviewer(proofId, itemId))
                .isInstanceOf(ProofNotFoundException.class);
    }

    @Test
    @DisplayName("listForReviewer: throws 404 when item does not belong to proof")
    void listForReviewerItemNotFound() {
        UUID proofId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();

        EmployeeProofOfInvestment proof =
                new EmployeeProofOfInvestment(TENANT_ID, UUID.randomUUID(), UUID.randomUUID(), "2026-2027", "test");
        given(proofRepository.findByTenantIdAndId(TENANT_ID, proofId)).willReturn(Optional.of(proof));
        given(itemRepository.findByTenantIdAndProofIdAndId(TENANT_ID, proofId, itemId))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> commentService.listForReviewer(proofId, itemId))
                .isInstanceOf(ProofNotFoundException.class);
    }

    @Test
    @DisplayName("addForReviewer: saves comment with REVIEWER role and reviewer's employee ID")
    void addForReviewerSuccess() {
        UUID proofId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();

        EmployeeResponse reviewer = stubEmployee(reviewerId, "hr@example.com");
        given(employeeService.currentEmployee()).willReturn(Optional.of(reviewer));

        EmployeeProofOfInvestment proof =
                new EmployeeProofOfInvestment(TENANT_ID, UUID.randomUUID(), UUID.randomUUID(), "2026-2027", "test");
        EmployeeProofItem item = new EmployeeProofItem(
                TENANT_ID,
                proofId,
                ProofSourceKind.SECTION_6A,
                UUID.randomUUID(),
                "PPF",
                java.math.BigDecimal.TEN,
                "test");

        given(proofRepository.findByTenantIdAndId(TENANT_ID, proofId)).willReturn(Optional.of(proof));
        given(itemRepository.findByTenantIdAndProofIdAndId(TENANT_ID, proofId, itemId))
                .willReturn(Optional.of(item));

        given(commentRepository.save(any(EmployeeProofItemComment.class))).willAnswer(invocation -> {
            EmployeeProofItemComment c = invocation.getArgument(0);
            ReflectionTestUtils.setField(c, "id", UUID.randomUUID());
            ReflectionTestUtils.setField(c, "createdAt", Instant.now());
            return c;
        });

        ProofCommentRequest req = new ProofCommentRequest("Receipt date is unclear");
        ProofCommentResponse response = commentService.addForReviewer(proofId, itemId, req);

        assertThat(response.body()).isEqualTo("Receipt date is unclear");
        assertThat(response.authorRole()).isEqualTo(ProofCommentRole.REVIEWER);
        assertThat(response.authorEmployeeId()).isEqualTo(reviewerId);

        ArgumentCaptor<EmployeeProofItemComment> captor = ArgumentCaptor.forClass(EmployeeProofItemComment.class);
        verify(commentRepository).save(captor.capture());
        assertThat(captor.getValue().getAuthorRole()).isEqualTo(ProofCommentRole.REVIEWER);
        assertThat(captor.getValue().getCreatedBy()).isEqualTo("hr@example.com");
    }

    @Test
    @DisplayName("addForReviewer: validates body length (1 to 1000 characters)")
    void addForReviewerValidatesBody() {
        UUID proofId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();

        assertThatThrownBy(() -> commentService.addForReviewer(proofId, itemId, new ProofCommentRequest("   ")))
                .isInstanceOf(ProofValidationException.class);

        assertThatThrownBy(() -> commentService.addForReviewer(proofId, itemId, new ProofCommentRequest(null)))
                .isInstanceOf(ProofValidationException.class);

        assertThatThrownBy(
                        () -> commentService.addForReviewer(proofId, itemId, new ProofCommentRequest("a".repeat(1001))))
                .isInstanceOf(ProofValidationException.class);
    }

    @Test
    @DisplayName("addForOwn: saves comment with EMPLOYEE role and own employee ID")
    void addForOwnSuccess() {
        UUID empId = UUID.randomUUID();
        UUID proofId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();

        EmployeeResponse me = stubEmployee(empId, "employee@example.com");
        given(employeeService.currentEmployee()).willReturn(Optional.of(me));

        EmployeeProofOfInvestment proof =
                new EmployeeProofOfInvestment(TENANT_ID, empId, UUID.randomUUID(), "2026-2027", "test");
        ReflectionTestUtils.setField(proof, "id", proofId);

        EmployeeProofItem item = new EmployeeProofItem(
                TENANT_ID,
                proofId,
                ProofSourceKind.SECTION_6A,
                UUID.randomUUID(),
                "PPF",
                java.math.BigDecimal.TEN,
                "test");

        given(proofRepository.findByTenantIdAndEmployeeIdAndFinancialYear(TENANT_ID, empId, "2026-2027"))
                .willReturn(Optional.of(proof));
        given(itemRepository.findByTenantIdAndProofIdAndId(TENANT_ID, proofId, itemId))
                .willReturn(Optional.of(item));

        given(commentRepository.save(any(EmployeeProofItemComment.class))).willAnswer(invocation -> {
            EmployeeProofItemComment c = invocation.getArgument(0);
            ReflectionTestUtils.setField(c, "id", UUID.randomUUID());
            ReflectionTestUtils.setField(c, "createdAt", Instant.now());
            return c;
        });

        ProofCommentRequest req = new ProofCommentRequest("Re-uploaded with clear scan");
        ProofCommentResponse response = commentService.addForOwn("2026-2027", itemId, req);

        assertThat(response.body()).isEqualTo("Re-uploaded with clear scan");
        assertThat(response.authorRole()).isEqualTo(ProofCommentRole.EMPLOYEE);
        assertThat(response.authorEmployeeId()).isEqualTo(empId);

        ArgumentCaptor<EmployeeProofItemComment> captor = ArgumentCaptor.forClass(EmployeeProofItemComment.class);
        verify(commentRepository).save(captor.capture());
        assertThat(captor.getValue().getAuthorRole()).isEqualTo(ProofCommentRole.EMPLOYEE);
        assertThat(captor.getValue().getCreatedBy()).isEqualTo("employee@example.com");
    }

    @Test
    @DisplayName("addForOwn: throws 404 when item belongs to another employee's proof")
    void addForOwnOtherEmployeeItem() {
        UUID empId = UUID.randomUUID();
        UUID proofId = UUID.randomUUID();
        UUID otherItemId = UUID.randomUUID();

        EmployeeResponse me = stubEmployee(empId, "employee@example.com");
        given(employeeService.currentEmployee()).willReturn(Optional.of(me));

        EmployeeProofOfInvestment proof =
                new EmployeeProofOfInvestment(TENANT_ID, empId, UUID.randomUUID(), "2026-2027", "test");
        ReflectionTestUtils.setField(proof, "id", proofId);

        given(proofRepository.findByTenantIdAndEmployeeIdAndFinancialYear(TENANT_ID, empId, "2026-2027"))
                .willReturn(Optional.of(proof));
        given(itemRepository.findByTenantIdAndProofIdAndId(TENANT_ID, proofId, otherItemId))
                .willReturn(Optional.empty());

        assertThatThrownBy(() ->
                        commentService.addForOwn("2026-2027", otherItemId, new ProofCommentRequest("Sneak comment")))
                .isInstanceOf(ProofNotFoundException.class);
    }
}
