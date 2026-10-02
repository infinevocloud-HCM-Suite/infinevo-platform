package com.infinevo.payroll.proof;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.EmploymentStatus;
import com.infinevo.payroll.taxdeclaration.DeclarationStatus;
import com.infinevo.payroll.taxdeclaration.IncomeTaxDeclarationWindow;
import com.infinevo.payroll.taxdeclaration.IncomeTaxDeclarationWindowRepository;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationRules;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

/**
 * Unit tests for {@link ProofPendingQueryImpl} (W-34.3 spec sections 3 and 4).
 */
@ExtendWith(MockitoExtension.class)
class ProofPendingQueryTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String FY = "2026-2027";

    @Mock
    private EntityManager entityManager;

    @Mock
    private IncomeTaxDeclarationWindowRepository windowRepository;

    private ProofPendingQueryImpl query;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-06-15T04:30:00Z"), TaxDeclarationRules.ZONE);
        query = new ProofPendingQueryImpl(entityManager, windowRepository, clock);
    }

    @Test
    @DisplayName("findPendingEmployeeIds binds all required parameters without untyped nulls")
    @SuppressWarnings("unchecked")
    void findPendingEmployeeIdsBindsParameters() {
        TypedQuery<UUID> typedQuery = mock(TypedQuery.class);
        ArgumentCaptor<String> jpqlCaptor = ArgumentCaptor.forClass(String.class);

        when(entityManager.createQuery(jpqlCaptor.capture(), eq(UUID.class))).thenReturn(typedQuery);
        when(typedQuery.setParameter(anyString(), any())).thenReturn(typedQuery);
        UUID empId = UUID.randomUUID();
        when(typedQuery.getResultList()).thenReturn(List.of(empId));

        List<UUID> results = query.findPendingEmployeeIds(TENANT_ID, FY);

        assertThat(results).containsExactly(empId);
        String jpql = jpqlCaptor.getValue();
        assertThat(jpql)
                .contains("d.status = :submittedStatus")
                .contains("e.status = :activeStatus")
                .contains("e.deleted = false")
                .contains("(p.id IS NULL OR p.status IN (:pendingProofStatuses))");

        verify(typedQuery).setParameter("tenantId", TENANT_ID);
        verify(typedQuery).setParameter("financialYear", FY);
        verify(typedQuery).setParameter("submittedStatus", DeclarationStatus.SUBMITTED);
        verify(typedQuery).setParameter("activeStatus", EmploymentStatus.ACTIVE);
        verify(typedQuery).setParameter("pendingProofStatuses", List.of(ProofStatus.DRAFT, ProofStatus.REJECTED));
    }

    @Test
    @DisplayName("findChaseRows throws 400 validation exception for unknown status filter")
    void findChaseRowsRejectsInvalidStatusFilter() {
        assertThatThrownBy(() -> query.findChaseRows(TENANT_ID, FY, "INVALID_STATUS", null, PageRequest.of(0, 25)))
                .isInstanceOf(ProofValidationException.class)
                .hasMessageContaining("Unknown status filter");
    }

    @Test
    @DisplayName("findChaseRows maps rows correctly and reports NOT_STARTED when proof is null")
    void findChaseRowsMapsRowsCorrectly() {
        Query countQuery = mock(Query.class);
        Query dataQuery = mock(Query.class);

        when(entityManager.createQuery(anyString())).thenReturn(countQuery, dataQuery);
        when(countQuery.setParameter(anyString(), any())).thenReturn(countQuery);
        when(countQuery.getSingleResult()).thenReturn(2L);

        when(dataQuery.setParameter(anyString(), any())).thenReturn(dataQuery);
        when(dataQuery.setFirstResult(0)).thenReturn(dataQuery);
        when(dataQuery.setMaxResults(25)).thenReturn(dataQuery);

        UUID emp1 = UUID.randomUUID();
        UUID emp2 = UUID.randomUUID();
        UUID proof2 = UUID.randomUUID();
        Instant submittedAt = Instant.parse("2026-06-10T10:00:00Z");

        // Row 1: No proof -> NOT_STARTED
        Object[] r1 = new Object[] {emp1, "EMP-001", "Alice", "Smith", "NEW", null, null, null, BigDecimal.ZERO, null};
        // Row 2: Approved proof
        Object[] r2 = new Object[] {
            emp2,
            "EMP-002",
            "Bob",
            null,
            "OLD",
            proof2,
            ProofStatus.APPROVED,
            submittedAt,
            new BigDecimal("150000.0000"),
            new BigDecimal("120000.0000")
        };

        when(dataQuery.getResultList()).thenReturn(List.of(r1, r2));

        Page<ProofChaseRow> page = query.findChaseRows(TENANT_ID, FY, null, null, PageRequest.of(0, 25));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(2);

        ProofChaseRow row1 = page.getContent().get(0);
        assertThat(row1.employeeId()).isEqualTo(emp1);
        assertThat(row1.number()).isEqualTo("EMP-001");
        assertThat(row1.name()).isEqualTo("Alice Smith");
        assertThat(row1.proofStatus()).isEqualTo("NOT_STARTED");
        assertThat(row1.proofId()).isNull();
        assertThat(row1.submittedAt()).isNull();
        assertThat(row1.claimedTotal()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(row1.approvedTotal()).isNull();

        ProofChaseRow row2 = page.getContent().get(1);
        assertThat(row2.employeeId()).isEqualTo(emp2);
        assertThat(row2.number()).isEqualTo("EMP-002");
        assertThat(row2.name()).isEqualTo("Bob");
        assertThat(row2.proofStatus()).isEqualTo("APPROVED");
        assertThat(row2.proofId()).isEqualTo(proof2);
        assertThat(row2.submittedAt()).isEqualTo(submittedAt);
        assertThat(row2.claimedTotal()).isEqualByComparingTo(new BigDecimal("150000"));
        assertThat(row2.approvedTotal()).isEqualByComparingTo(new BigDecimal("120000"));
    }

    @Test
    @DisplayName("getSummary returns correct counts and window state")
    void getSummaryReturnsCountsAndWindowState() {
        Query summaryQuery = mock(Query.class);
        when(entityManager.createQuery(anyString())).thenReturn(summaryQuery);
        when(summaryQuery.setParameter(anyString(), any())).thenReturn(summaryQuery);

        Object[] counts = new Object[] {5L, 2L, 8L, 10L, 1L};
        when(summaryQuery.getSingleResult()).thenReturn(counts);

        IncomeTaxDeclarationWindow window = new IncomeTaxDeclarationWindow();
        window.setPoiDueDate(LocalDate.of(2027, 1, 31));
        window.setPoiOpensOn(LocalDate.of(2026, 6, 1));
        window.setPoiLocked(false);

        when(windowRepository.findByTenantIdAndFinancialYear(TENANT_ID, FY)).thenReturn(Optional.of(window));

        ProofChaseSummary summary = query.getSummary(TENANT_ID, FY);

        assertThat(summary.notStarted()).isEqualTo(5L);
        assertThat(summary.draft()).isEqualTo(2L);
        assertThat(summary.submitted()).isEqualTo(8L);
        assertThat(summary.approved()).isEqualTo(10L);
        assertThat(summary.rejected()).isEqualTo(1L);
        assertThat(summary.dueDate()).isEqualTo(LocalDate.of(2027, 1, 31));
        assertThat(summary.proofOpen()).isTrue();
        assertThat(summary.counts())
                .containsEntry("NOT_STARTED", 5L)
                .containsEntry("DRAFT", 2L)
                .containsEntry("SUBMITTED", 8L)
                .containsEntry("APPROVED", 10L)
                .containsEntry("REJECTED", 1L);
    }

    @Test
    @DisplayName("findChaseRows assigns approvedTotal as ZERO when approved proof has null approved items sum")
    void findChaseRowsApprovedProofWithNullApprovedSumGetsZero() {
        Query countQuery = mock(Query.class);
        Query dataQuery = mock(Query.class);

        when(entityManager.createQuery(anyString())).thenReturn(countQuery, dataQuery);
        when(countQuery.setParameter(anyString(), any())).thenReturn(countQuery);
        when(countQuery.getSingleResult()).thenReturn(1L);

        when(dataQuery.setParameter(anyString(), any())).thenReturn(dataQuery);
        when(dataQuery.setFirstResult(0)).thenReturn(dataQuery);
        when(dataQuery.setMaxResults(25)).thenReturn(dataQuery);

        UUID empId = UUID.randomUUID();
        UUID proofId = UUID.randomUUID();

        // Row: Approved proof, but r[9] (approved items sum) is null
        Object[] r = new Object[] {
            empId,
            "EMP-003",
            "Carol",
            "Danvers",
            "NEW",
            proofId,
            ProofStatus.APPROVED,
            Instant.now(),
            BigDecimal.ZERO,
            null
        };

        when(dataQuery.getResultList()).thenReturn(List.of((Object) r));

        Page<ProofChaseRow> page = query.findChaseRows(TENANT_ID, FY, null, null, PageRequest.of(0, 25));

        assertThat(page.getContent()).hasSize(1);
        ProofChaseRow row = page.getContent().get(0);
        assertThat(row.proofStatus()).isEqualTo("APPROVED");
        assertThat(row.approvedTotal()).isNotNull();
        assertThat(row.approvedTotal()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
