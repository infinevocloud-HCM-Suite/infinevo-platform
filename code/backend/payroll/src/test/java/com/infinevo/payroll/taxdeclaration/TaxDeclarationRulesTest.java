package com.infinevo.payroll.taxdeclaration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.payroll.taxdeclaration.dto.TaxDeclarationWindowRequest;
import com.infinevo.payroll.taxdeclaration.exception.WindowValidationException;
import com.infinevo.shared.tenant.TenantContext;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Unit tests for tax declaration editability rules, regime constraints, and window validations (W-32.1, spec §7).
 */
class TaxDeclarationRulesTest {

    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID EMPLOYEE_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final String FY = "2025-2026";

    private final LocalDate opens = LocalDate.of(2025, 4, 1);
    private final LocalDate closes = LocalDate.of(2025, 4, 30);

    @Test
    @DisplayName("editable: false when status is SUBMITTED")
    void editableFalseWhenSubmitted() {
        EmployeeInvestmentDeclaration header = new EmployeeInvestmentDeclaration(TENANT_ID, EMPLOYEE_ID, FY, "NEW");
        header.setStatus(DeclarationStatus.SUBMITTED);
        IncomeTaxDeclarationWindow window =
                new IncomeTaxDeclarationWindow(TENANT_ID, FY, opens, closes, "NEW", true, true);

        assertThat(TaxDeclarationRules.isEditable(header, window, LocalDate.of(2025, 4, 15), false))
                .isFalse();
    }

    @Test
    @DisplayName("editable: false when header is locked by officer")
    void editableFalseWhenHeaderLocked() {
        EmployeeInvestmentDeclaration header = new EmployeeInvestmentDeclaration(TENANT_ID, EMPLOYEE_ID, FY, "NEW");
        header.setLocked(true);
        IncomeTaxDeclarationWindow window =
                new IncomeTaxDeclarationWindow(TENANT_ID, FY, opens, closes, "NEW", true, true);

        assertThat(TaxDeclarationRules.isEditable(header, window, LocalDate.of(2025, 4, 15), false))
                .isFalse();
    }

    @Test
    @DisplayName("editable: false when window is locked")
    void editableFalseWhenWindowLocked() {
        EmployeeInvestmentDeclaration header = new EmployeeInvestmentDeclaration(TENANT_ID, EMPLOYEE_ID, FY, "NEW");
        IncomeTaxDeclarationWindow window =
                new IncomeTaxDeclarationWindow(TENANT_ID, FY, opens, closes, "NEW", true, true);
        window.setLocked(true);

        assertThat(TaxDeclarationRules.isEditable(header, window, LocalDate.of(2025, 4, 15), false))
                .isFalse();
    }

    @Test
    @DisplayName("editable: false before window opens")
    void editableFalseBeforeOpens() {
        EmployeeInvestmentDeclaration header = new EmployeeInvestmentDeclaration(TENANT_ID, EMPLOYEE_ID, FY, "NEW");
        IncomeTaxDeclarationWindow window =
                new IncomeTaxDeclarationWindow(TENANT_ID, FY, opens, closes, "NEW", true, true);

        assertThat(TaxDeclarationRules.isEditable(header, window, LocalDate.of(2025, 3, 31), false))
                .isFalse();
    }

    @Test
    @DisplayName("editable: false after window closes")
    void editableFalseAfterCloses() {
        EmployeeInvestmentDeclaration header = new EmployeeInvestmentDeclaration(TENANT_ID, EMPLOYEE_ID, FY, "NEW");
        IncomeTaxDeclarationWindow window =
                new IncomeTaxDeclarationWindow(TENANT_ID, FY, opens, closes, "NEW", true, true);

        assertThat(TaxDeclarationRules.isEditable(header, window, LocalDate.of(2025, 5, 1), false))
                .isFalse();
    }

    @Test
    @DisplayName("editable: true on both boundary days (opening and closing dates)")
    void editableTrueOnBoundaryDays() {
        EmployeeInvestmentDeclaration header = new EmployeeInvestmentDeclaration(TENANT_ID, EMPLOYEE_ID, FY, "NEW");
        IncomeTaxDeclarationWindow window =
                new IncomeTaxDeclarationWindow(TENANT_ID, FY, opens, closes, "NEW", true, true);

        // Opening day
        assertThat(TaxDeclarationRules.isEditable(header, window, opens, false)).isTrue();
        // Closing day
        assertThat(TaxDeclarationRules.isEditable(header, window, closes, false))
                .isTrue();
    }

    @Test
    @DisplayName("ignoreWindow skips date and window lock test, but header lock still blocks")
    void ignoreWindowSkipsDateTestOnly() {
        EmployeeInvestmentDeclaration header = new EmployeeInvestmentDeclaration(TENANT_ID, EMPLOYEE_ID, FY, "NEW");
        IncomeTaxDeclarationWindow window =
                new IncomeTaxDeclarationWindow(TENANT_ID, FY, opens, closes, "NEW", true, true);

        // Date is outside the window (May 10)
        LocalDate dateOutside = LocalDate.of(2025, 5, 10);
        assertThat(TaxDeclarationRules.isEditable(header, window, dateOutside, false))
                .isFalse();
        assertThat(TaxDeclarationRules.isEditable(header, window, dateOutside, true))
                .isTrue();

        // But if header is locked, ignoreWindow still returns false
        header.setLocked(true);
        assertThat(TaxDeclarationRules.isEditable(header, window, dateOutside, true))
                .isFalse();
    }

    @Test
    @DisplayName("ignoreWindow does NOT allow editing a SUBMITTED declaration; must reopen to DRAFT first")
    void ignoreWindowRefusesEditingSubmittedDeclaration() {
        EmployeeInvestmentDeclaration header = new EmployeeInvestmentDeclaration(TENANT_ID, EMPLOYEE_ID, FY, "NEW");
        header.setStatus(DeclarationStatus.SUBMITTED);
        IncomeTaxDeclarationWindow window =
                new IncomeTaxDeclarationWindow(TENANT_ID, FY, opens, closes, "NEW", true, true);

        // Neither employee (ignoreWindow=false) nor officer (ignoreWindow=true) can edit submitted declaration
        assertThat(TaxDeclarationRules.isEditable(header, window, LocalDate.of(2025, 4, 15), false))
                .isFalse();
        assertThat(TaxDeclarationRules.isEditable(header, window, LocalDate.of(2025, 4, 15), true))
                .isFalse();

        // Once reopened to DRAFT, officer can edit even if window is closed
        header.setStatus(DeclarationStatus.DRAFT);
        assertThat(TaxDeclarationRules.isEditable(header, window, LocalDate.of(2025, 5, 10), true))
                .isTrue();
    }

    @Test
    @DisplayName("Window validation refuses closes-before-opens date range")
    void refusesWindowClosesBeforeOpens() {
        IncomeTaxDeclarationWindowRepository repo = Mockito.mock(IncomeTaxDeclarationWindowRepository.class);
        TaxDeclarationWindowService service = new TaxDeclarationWindowServiceImpl(repo);

        TenantContext.set(TENANT_ID);
        try {
            TaxDeclarationWindowRequest req = new TaxDeclarationWindowRequest(
                    LocalDate.of(2025, 5, 10),
                    LocalDate.of(2025, 5, 1), // closes before opens
                    false,
                    "NEW",
                    true,
                    true,
                    false,
                    false);

            assertThatThrownBy(() -> service.upsert("2025-2026", req))
                    .isInstanceOf(WindowValidationException.class)
                    .hasMessageContaining("closing date cannot be before opening date");
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("Window validation refuses dates outside the financial year")
    void refusesWindowDatesOutsideFinancialYear() {
        IncomeTaxDeclarationWindowRepository repo = Mockito.mock(IncomeTaxDeclarationWindowRepository.class);
        TaxDeclarationWindowService service = new TaxDeclarationWindowServiceImpl(repo);

        TenantContext.set(TENANT_ID);
        try {
            // Opens before FY starts (2025-03-31 < 2025-04-01)
            TaxDeclarationWindowRequest reqBefore = new TaxDeclarationWindowRequest(
                    LocalDate.of(2025, 3, 31), LocalDate.of(2025, 4, 30), false, "NEW", true, true, false, false);

            assertThatThrownBy(() -> service.upsert("2025-2026", reqBefore))
                    .isInstanceOf(WindowValidationException.class)
                    .hasMessageContaining("must fall within financial year");

            // Closes after FY ends (2026-04-01 > 2026-03-31)
            TaxDeclarationWindowRequest reqAfter = new TaxDeclarationWindowRequest(
                    LocalDate.of(2025, 4, 1), LocalDate.of(2026, 4, 1), false, "NEW", true, true, false, false);

            assertThatThrownBy(() -> service.upsert("2025-2026", reqAfter))
                    .isInstanceOf(WindowValidationException.class)
                    .hasMessageContaining("must fall within financial year");
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("Regime change is refused with REGIME_CHANGE_NOT_ALLOWED when window forbids it")
    void refusesRegimeChangeWhenWindowForbids() {
        EmployeeInvestmentDeclarationRepository declRepo = Mockito.mock(EmployeeInvestmentDeclarationRepository.class);
        TaxDeclarationWindowService windowService = Mockito.mock(TaxDeclarationWindowService.class);
        com.infinevo.core.employee.EmployeeService employeeService =
                Mockito.mock(com.infinevo.core.employee.EmployeeService.class);

        TaxDeclarationService service = new TaxDeclarationServiceImpl(
                declRepo,
                windowService,
                employeeService,
                Mockito.mock(com.infinevo.payroll.taxdeclaration.housing.HraRuleReader.class));

        TenantContext.set(TENANT_ID);
        try {
            EmployeeInvestmentDeclaration existingHeader =
                    new EmployeeInvestmentDeclaration(TENANT_ID, EMPLOYEE_ID, FY, "NEW");
            Mockito.when(declRepo.findByTenantIdAndEmployeeIdAndFinancialYear(TENANT_ID, EMPLOYEE_ID, FY))
                    .thenReturn(Optional.of(existingHeader));

            // Window allows edits (open) but forbids regime change
            IncomeTaxDeclarationWindow window = new IncomeTaxDeclarationWindow(
                    TENANT_ID, FY, opens, closes, "NEW", false /* canChangeTaxRegime = false */, true);
            Mockito.when(windowService.findOrCreateDefault(TENANT_ID, FY)).thenReturn(window);

            com.infinevo.payroll.taxdeclaration.dto.TaxDeclarationRequest changeReq =
                    new com.infinevo.payroll.taxdeclaration.dto.TaxDeclarationRequest("OLD", null, null, null);

            assertThatThrownBy(() -> service.save(EMPLOYEE_ID, FY, changeReq))
                    .isInstanceOf(com.infinevo.payroll.taxdeclaration.exception.DeclarationNotEditableException.class)
                    .satisfies(e -> {
                        com.infinevo.payroll.taxdeclaration.exception.DeclarationNotEditableException ex =
                                (com.infinevo.payroll.taxdeclaration.exception.DeclarationNotEditableException) e;
                        assertThat(ex.reasonCode()).isEqualTo("REGIME_CHANGE_NOT_ALLOWED");
                    });
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName(
            "Header carries the rent PAN rule: the window flag and the hra_rule_master threshold, null with no row")
    void headerCarriesRentPanRule() {
        EmployeeInvestmentDeclarationRepository declRepo = Mockito.mock(EmployeeInvestmentDeclarationRepository.class);
        TaxDeclarationWindowService windowService = Mockito.mock(TaxDeclarationWindowService.class);
        com.infinevo.core.employee.EmployeeService employeeService =
                Mockito.mock(com.infinevo.core.employee.EmployeeService.class);
        com.infinevo.payroll.taxdeclaration.housing.HraRuleReader hraRuleReader =
                Mockito.mock(com.infinevo.payroll.taxdeclaration.housing.HraRuleReader.class);

        TaxDeclarationService service =
                new TaxDeclarationServiceImpl(declRepo, windowService, employeeService, hraRuleReader);

        TenantContext.set(TENANT_ID);
        try {
            EmployeeInvestmentDeclaration header = new EmployeeInvestmentDeclaration(TENANT_ID, EMPLOYEE_ID, FY, "OLD");
            Mockito.when(declRepo.findByTenantIdAndEmployeeIdAndFinancialYear(TENANT_ID, EMPLOYEE_ID, FY))
                    .thenReturn(Optional.of(header));
            IncomeTaxDeclarationWindow window =
                    new IncomeTaxDeclarationWindow(TENANT_ID, FY, opens, closes, "NEW", true, true);
            Mockito.when(windowService.findOrCreateDefault(TENANT_ID, FY)).thenReturn(window);
            Mockito.when(hraRuleReader.findPanMandatoryThreshold(FY, "OLD"))
                    .thenReturn(new java.math.BigDecimal("100000.0000"));

            com.infinevo.payroll.taxdeclaration.dto.TaxDeclarationResponse withRule = service.read(EMPLOYEE_ID, FY);
            assertThat(withRule.panRequiredForRentOverThreshold()).isTrue();
            assertThat(withRule.rentPanThreshold()).isEqualByComparingTo("100000");

            // Tenant switched the rule off and no reference row exists for the year
            window.setPanRequiredForRentOverThreshold(false);
            Mockito.when(hraRuleReader.findPanMandatoryThreshold(FY, "OLD")).thenReturn(null);

            com.infinevo.payroll.taxdeclaration.dto.TaxDeclarationResponse withoutRule = service.read(EMPLOYEE_ID, FY);
            assertThat(withoutRule.panRequiredForRentOverThreshold()).isFalse();
            assertThat(withoutRule.rentPanThreshold()).isNull();
        } finally {
            TenantContext.clear();
        }
    }
}
