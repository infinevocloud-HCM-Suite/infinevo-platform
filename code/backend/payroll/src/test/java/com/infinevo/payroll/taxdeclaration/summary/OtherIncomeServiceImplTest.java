package com.infinevo.payroll.taxdeclaration.summary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.payroll.taxdeclaration.DeclarationStatus;
import com.infinevo.payroll.taxdeclaration.EmployeeInvestmentDeclaration;
import com.infinevo.payroll.taxdeclaration.IncomeTaxDeclarationWindow;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationService;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationWindowService;
import com.infinevo.payroll.taxdeclaration.exception.WindowValidationException;
import com.infinevo.payroll.taxdeclaration.summary.dto.OtherIncomeRequest;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OtherIncomeServiceImplTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();
    private final UUID declarationId = UUID.randomUUID();
    private final String fy = "2024-2025";

    private TaxDeclarationService taxDeclarationService;
    private EmployeeInvOtherIncomeRepository otherIncomeRepository;
    private TaxDeclarationWindowService windowService;
    private EmployeeService employeeService;

    private OtherIncomeServiceImpl otherIncomeService;

    @BeforeEach
    void setUp() {
        TenantContext.set(tenantId);

        taxDeclarationService = mock(TaxDeclarationService.class);
        otherIncomeRepository = mock(EmployeeInvOtherIncomeRepository.class);
        windowService = mock(TaxDeclarationWindowService.class);
        employeeService = mock(EmployeeService.class);

        otherIncomeService = new OtherIncomeServiceImpl(
                taxDeclarationService, otherIncomeRepository, windowService, employeeService);

        EmployeeResponse employee = mock(EmployeeResponse.class);
        given(employee.id()).willReturn(employeeId);
        given(employeeService.currentEmployee()).willReturn(Optional.of(employee));
        given(employeeService.get(employeeId)).willReturn(employee);

        EmployeeInvestmentDeclaration decl = new EmployeeInvestmentDeclaration(tenantId, employeeId, fy, "NEW");
        decl.setId(declarationId);
        decl.setStatus(DeclarationStatus.DRAFT);
        given(taxDeclarationService.require(employeeId, fy)).willReturn(decl);

        IncomeTaxDeclarationWindow window = new IncomeTaxDeclarationWindow(
                tenantId, fy, LocalDate.now().minusDays(5), LocalDate.now().plusDays(25), "NEW", false, false);
        given(windowService.findOrCreateDefault(tenantId, fy)).willReturn(window);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("replace() nulls out description for non-OTHER kinds and preserves for OTHER")
    void testReplaceNullsDescriptionForNonOther() {
        List<OtherIncomeRequest> requests = List.of(
                new OtherIncomeRequest(
                        OtherIncomeKind.SAVINGS_INTEREST, "Interest from SBI", new BigDecimal("5000.0000")),
                new OtherIncomeRequest(OtherIncomeKind.OTHER, "  Freelance writing  ", new BigDecimal("12000.0000")));

        otherIncomeService.replaceOwn(fy, requests);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<EmployeeInvOtherIncome>> captor = ArgumentCaptor.forClass(List.class);
        verify(otherIncomeRepository).saveAll(captor.capture());

        List<EmployeeInvOtherIncome> saved = captor.getValue();
        assertThat(saved).hasSize(2);

        EmployeeInvOtherIncome savings = saved.stream()
                .filter(i -> i.getKind() == OtherIncomeKind.SAVINGS_INTEREST)
                .findFirst()
                .orElseThrow();
        assertThat(savings.getDescription()).isNull();

        EmployeeInvOtherIncome other = saved.stream()
                .filter(i -> i.getKind() == OtherIncomeKind.OTHER)
                .findFirst()
                .orElseThrow();
        assertThat(other.getDescription()).isEqualTo("Freelance writing");
    }

    @Test
    @DisplayName("replace() rejects description exceeding 150 characters with 400 validation error")
    void testReplaceRejectsDescriptionOver150() {
        List<OtherIncomeRequest> requests =
                List.of(new OtherIncomeRequest(OtherIncomeKind.OTHER, "x".repeat(151), new BigDecimal("5000.0000")));

        assertThatThrownBy(() -> otherIncomeService.replaceOwn(fy, requests))
                .isInstanceOf(WindowValidationException.class)
                .hasMessageContaining("Description must not exceed 150 characters");
    }
}
