package com.infinevo.payroll.reimbursement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.infinevo.core.approval.ApprovalService;
import com.infinevo.core.document.DocumentService;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.payroll.PayrollTestSchema;
import com.infinevo.payroll.component.Reimbursement;
import com.infinevo.payroll.component.ReimbursementRepository;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

/** W-47.4 §4 / §7 — the claimable-components read and the one-call {@code employee_name} fill. */
class ReimbursementClaimServiceImplTest {

    private static final UUID TENANT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TENANT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private ReimbursementClaimRepository claimRepository;
    private ReimbursementRepository reimbursementRepository;
    private EmployeeService employeeService;
    private ReimbursementClaimServiceImpl service;

    @BeforeEach
    void setUp() {
        claimRepository = mock(ReimbursementClaimRepository.class);
        reimbursementRepository = mock(ReimbursementRepository.class);
        employeeService = mock(EmployeeService.class);
        service = new ReimbursementClaimServiceImpl(
                claimRepository,
                reimbursementRepository,
                mock(ApprovalService.class),
                employeeService,
                mock(DocumentService.class));
        TenantContext.set(TENANT_A);
        UUID me = UUID.randomUUID();
        given(employeeService.currentEmployee())
                .willReturn(Optional.of(PayrollTestSchema.createTestEmployee(
                        me, TENANT_A, "EMP-001", "Alice", "Smith", "alice@example.com")));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("claimableComponents asks for the bound tenant's active, undeleted rows and returns only active ones")
    void activeOnly() {
        Reimbursement travel = component(TENANT_A, "TRAVEL", "Travel", true, false);
        Reimbursement retired = component(TENANT_A, "FUEL", "Fuel", false, false);
        given(reimbursementRepository.findAllByTenantIdAndActiveAndDeletedFalse(TENANT_A, true))
                .willReturn(List.of(travel, retired));

        List<ClaimableComponentResponse> result = service.claimableComponents();

        assertThat(result).extracting(ClaimableComponentResponse::code).containsExactly("TRAVEL");
        ClaimableComponentResponse row = result.get(0);
        assertThat(row.id()).isEqualTo(travel.getId());
        assertThat(row.name()).isEqualTo("Travel");
        assertThat(row.maxLimit()).isEqualByComparingTo("5000.00");
        verify(reimbursementRepository).findAllByTenantIdAndActiveAndDeletedFalse(TENANT_A, true);
    }

    @Test
    @DisplayName("claimableComponents skips deleted rows")
    void deletedSkipped() {
        Reimbursement live = component(TENANT_A, "MEAL", "Meals", true, false);
        Reimbursement deleted = component(TENANT_A, "PHONE", "Phone", true, true);
        given(reimbursementRepository.findAllByTenantIdAndActiveAndDeletedFalse(TENANT_A, true))
                .willReturn(List.of(deleted, live));

        assertThat(service.claimableComponents())
                .extracting(ClaimableComponentResponse::code)
                .containsExactly("MEAL");
    }

    @Test
    @DisplayName("claimableComponents never returns another tenant's rows and never asks for another tenant")
    void otherTenantNever() {
        Reimbursement ours = component(TENANT_A, "TRAVEL", "Travel", true, false);
        Reimbursement theirs = component(TENANT_B, "TRAVEL_B", "Travel B", true, false);
        given(reimbursementRepository.findAllByTenantIdAndActiveAndDeletedFalse(TENANT_A, true))
                .willReturn(List.of(ours, theirs));

        assertThat(service.claimableComponents())
                .extracting(ClaimableComponentResponse::code)
                .containsExactly("TRAVEL");
        verify(reimbursementRepository, never()).findAllByTenantIdAndActiveAndDeletedFalse(TENANT_B, true);
        verify(reimbursementRepository, never()).findAll();
    }

    @Test
    @DisplayName("claimableComponents refuses a login linked to no employee before reading anything")
    void unlinkedLoginRefused() {
        given(employeeService.currentEmployee()).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.claimableComponents()).isInstanceOf(AccessDeniedException.class);
        verify(reimbursementRepository, never()).findAllByTenantIdAndActiveAndDeletedFalse(any(), anyBoolean());
    }

    @Test
    @DisplayName("claimableComponents with no tenant bound fails and reads nothing")
    void noTenantFails() {
        TenantContext.clear();

        assertThatThrownBy(() -> service.claimableComponents()).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(reimbursementRepository);
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("list fills employee_name for a whole page from one displayNames call")
    void listFillsNamesInOneCall() {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        Reimbursement travel = component(TENANT_A, "TRAVEL", "Travel", true, false);
        ReimbursementClaim one = claim(alice, travel.getId());
        ReimbursementClaim two = claim(bob, travel.getId());
        ReimbursementClaim three = claim(alice, travel.getId());
        Pageable pageable = PageRequest.of(0, 25);
        given(claimRepository.findAll(any(Specification.class), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(one, two, three), pageable, 3));
        given(reimbursementRepository.findAllById(any())).willReturn(List.of(travel));
        given(employeeService.displayNames(anyCollection())).willReturn(Map.of(alice, "Alice Smith", bob, "Bob Jones"));

        List<ReimbursementClaimResponse> rows =
                service.list(null, null, null, null, pageable).getContent();

        assertThat(rows)
                .extracting(ReimbursementClaimResponse::employeeName)
                .containsExactly("Alice Smith", "Bob Jones", "Alice Smith");
        verify(employeeService, times(1)).displayNames(anyCollection());
    }

    private static Reimbursement component(UUID tenantId, String code, String name, boolean active, boolean deleted) {
        Reimbursement r = new Reimbursement(tenantId, "test");
        ReflectionTestUtils.setField(r, "id", UUID.randomUUID());
        r.setCode(code);
        r.setName(name);
        r.setMaxLimit(new BigDecimal("5000.00"));
        r.setActive(active);
        r.setDeleted(deleted);
        return r;
    }

    private static ReimbursementClaim claim(UUID employeeId, UUID reimbursementId) {
        return new ReimbursementClaim(
                TENANT_A, employeeId, reimbursementId, new BigDecimal("100.00"), LocalDate.now(), "x", null, "test");
    }
}
