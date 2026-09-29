package com.infinevo.payroll.taxdeclaration.deductions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.core.employee.EmployeeService;
import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.taxdeclaration.FinancialYear;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationService;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationTestSchema;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationWindowService;
import com.infinevo.payroll.taxdeclaration.deductions.dto.DeductionDeclarationResponse;
import com.infinevo.payroll.taxdeclaration.deductions.dto.PreTaxDeductionRequest;
import com.infinevo.payroll.taxdeclaration.deductions.dto.PrevEmploymentRequest;
import com.infinevo.payroll.taxdeclaration.deductions.dto.Section6AItemResponse;
import com.infinevo.payroll.taxdeclaration.deductions.dto.Section6ALineRequest;
import com.infinevo.payroll.taxdeclaration.deductions.dto.Section6ALineResponse;
import com.infinevo.payroll.taxdeclaration.dto.TaxDeclarationWindowRequest;
import com.infinevo.payroll.taxdeclaration.exception.DeclarationNotEditableException;
import com.infinevo.payroll.taxdeclaration.exception.WindowValidationException;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Acceptance integration test for Section 6A, pre-tax deductions, and previous employment (W-32.3).
 */
@SpringBootTest(classes = PayrollTestApp.class)
class DeductionDeclarationIT extends AbstractIntegrationTest {

    @Autowired
    private TaxDeclarationWindowService windowService;

    @Autowired
    private TaxDeclarationService taxDeclarationService;

    @Autowired
    private DeductionDeclarationService deductionService;

    @Autowired
    private Section6AItemReader section6AItemReader;

    @Autowired
    private EmployeeService employeeService;

    private UUID employeeId;
    private String currentFy;

    @BeforeAll
    static void applySchema() throws Exception {
        TaxDeclarationTestSchema.apply();
    }

    @AfterAll
    static void tearDown() throws SQLException {
        TaxDeclarationTestSchema.clearAll();
    }

    @BeforeEach
    void setUp() throws SQLException {
        TenantContext.clear();
        TaxDeclarationTestSchema.seedTenants();
        TaxDeclarationTestSchema.clearDeclarations();

        TenantContext.set(TaxDeclarationTestSchema.TENANT_A);
        employeeId = TaxDeclarationTestSchema.seedEmployee(
                TaxDeclarationTestSchema.TENANT_A, "EMP-201", "deductions.emp@acme.com", "Ded", "Tester");

        FinancialYear fy = FinancialYear.of(LocalDate.now());
        currentFy = fy.label();

        // The real stand-in (PayrollTestApp) looks the employee up tenant-scoped, so every
        // employeeService.get(employeeId) ownership check in the services runs for real.
        PayrollTestApp.CURRENT_EMPLOYEE.set(employeeService.get(employeeId));

        LocalDate openDate = LocalDate.now().minusDays(1);
        LocalDate closeDate = LocalDate.now().plusDays(30);
        if (openDate.isBefore(fy.start())) openDate = fy.start();
        if (closeDate.isAfter(fy.end())) closeDate = fy.end();

        TaxDeclarationWindowRequest winReq =
                new TaxDeclarationWindowRequest(openDate, closeDate, false, "OLD", true, true, false, false);
        windowService.upsert(currentFy, winReq);
    }

    @AfterEach
    void cleanUp() throws SQLException {
        PayrollTestApp.CURRENT_EMPLOYEE.remove();
        TaxDeclarationTestSchema.clearDeclarations();
        TenantContext.clear();
    }

    @Test
    @DisplayName(
            "Full deduction flow: catalogue read, Section 6A, pre-tax, prev employment, group caps, lock and officer entered guard")
    void testDeductionDeclarationLifecycle() {
        // 1. GET /section6a-items returns active catalogue items
        taxDeclarationService.readOwn(currentFy);
        List<Section6AItemResponse> items = deductionService.getSection6AItemsOwn(currentFy);
        assertThat(items).isNotEmpty();

        Section6AItemResponse item80c = items.stream()
                .filter(i -> "80C".equalsIgnoreCase(i.sectionCode()))
                .findFirst()
                .orElseThrow();
        assertThat(item80c.maxLimit()).isEqualByComparingTo(new BigDecimal("150000.0000"));
        assertThat(item80c.categoryGroupCode()).isEqualTo("80C_GROUP");

        Section6AItemResponse item80ccc = items.stream()
                .filter(i -> "80CCC".equalsIgnoreCase(i.sectionCode()))
                .findFirst()
                .orElseThrow();

        Section6AItemResponse item80d = items.stream()
                .filter(i -> "80D".equalsIgnoreCase(i.sectionCode()))
                .findFirst()
                .orElseThrow();

        // 2. PUT two 80C rows and one 80D row under the limits -> 200 OK
        List<Section6ALineRequest> validS6a = List.of(
                new Section6ALineRequest(item80c.id(), "LIC Policy 100", new BigDecimal("50000.0000")),
                new Section6ALineRequest(item80c.id(), "PPF Deposit", new BigDecimal("40000.0000")),
                new Section6ALineRequest(item80d.id(), "Mediclaim Self", new BigDecimal("25000.0000")));

        DeductionDeclarationResponse s6aResp = deductionService.replaceSection6AOwn(currentFy, validS6a);
        assertThat(s6aResp.section6a()).hasSize(3);
        assertThat(s6aResp.section6a())
                .extracting(Section6ALineResponse::description)
                .containsExactlyInAnyOrder("LIC Policy 100", "PPF Deposit", "Mediclaim Self");
        // Rows are ordered by created_at, then description; two rows saved in the same instant fall
        // back to description order, so assert by row rather than by position.
        assertThat(s6aResp.section6a())
                .filteredOn(r -> r.description().equals("LIC Policy 100"))
                .singleElement()
                .satisfies(r -> {
                    assertThat(r.sectionCode()).isEqualTo("80C");
                    assertThat(r.name()).isNotBlank();
                });
        assertThat(s6aResp.section6a())
                .filteredOn(r -> r.description().equals("Mediclaim Self"))
                .singleElement()
                .satisfies(r -> {
                    assertThat(r.sectionCode()).isEqualTo("80D");
                    assertThat(r.name()).isNotBlank();
                });

        // 3. Exceeding group umbrella cap (80C 100k + 80CCC 100k = 200k > 150k) -> 400
        List<Section6ALineRequest> overGroupCap = List.of(
                new Section6ALineRequest(item80c.id(), "LIC Policy", new BigDecimal("100000.0000")),
                new Section6ALineRequest(item80ccc.id(), "Annuity Plan", new BigDecimal("100000.0000")));
        assertThatThrownBy(() -> deductionService.replaceSection6AOwn(currentFy, overGroupCap))
                .isInstanceOf(WindowValidationException.class)
                .hasMessageContaining("exceeds group cap (150000.0000)");

        // 4. Declare pre-tax deductions
        List<PreTaxDeductionRequest> preTax = List.of(
                new PreTaxDeductionRequest(PreTaxDeductionKind.VPF, new BigDecimal("20000.0000")),
                new PreTaxDeductionRequest(PreTaxDeductionKind.NPS_EMPLOYEE, new BigDecimal("30000.0000")));
        DeductionDeclarationResponse preTaxResp = deductionService.replacePreTaxDeductionsOwn(currentFy, preTax);
        assertThat(preTaxResp.preTaxDeductions()).hasSize(2);

        // 5. Declare previous employment as employee
        List<PrevEmploymentRequest> prevEmp = List.of(
                new PrevEmploymentRequest(
                        PrevEmploymentKind.INCOME, new BigDecimal("500000.0000"), "Previous Tech Corp", "MUMB12345F"),
                new PrevEmploymentRequest(
                        PrevEmploymentKind.INCOME_TAX_DEDUCTED,
                        new BigDecimal("45000.0000"),
                        "Previous Tech Corp",
                        "MUMB12345F"));
        DeductionDeclarationResponse prevEmpResp = deductionService.replacePrevEmploymentOwn(currentFy, prevEmp);
        assertThat(prevEmpResp.previousEmployment()).hasSize(2);
        assertThat(prevEmpResp.previousEmployment().get(0).enteredBy()).isEqualTo(EnteredBy.EMPLOYEE);

        // 6. GET /deductions returns all three sections joined
        DeductionDeclarationResponse full = deductionService.readOwn(currentFy);
        assertThat(full.section6a()).hasSize(3);
        assertThat(full.preTaxDeductions()).hasSize(2);
        assertThat(full.previousEmployment()).hasSize(2);

        // 7. Submit declaration -> employee PUTs are rejected
        taxDeclarationService.submitOwn(currentFy);

        assertThatThrownBy(() -> deductionService.replaceSection6AOwn(currentFy, validS6a))
                .isInstanceOf(DeclarationNotEditableException.class);
        assertThatThrownBy(() -> deductionService.replacePreTaxDeductionsOwn(currentFy, preTax))
                .isInstanceOf(DeclarationNotEditableException.class);
        assertThatThrownBy(() -> deductionService.replacePrevEmploymentOwn(currentFy, prevEmp))
                .isInstanceOf(DeclarationNotEditableException.class);

        // 8. Officer enters previous employment details after reopening
        taxDeclarationService.reopen(employeeId, currentFy);
        deductionService.replacePrevEmployment(employeeId, currentFy, prevEmp);
        DeductionDeclarationResponse officerUpdated = deductionService.read(employeeId, currentFy);
        assertThat(officerUpdated.previousEmployment().get(0).enteredBy()).isEqualTo(EnteredBy.OFFICER);

        // 9. Employee reopens declaration to DRAFT
        taxDeclarationService.reopenOwn(currentFy);

        // Employee can update Section 6A and Pre-tax
        deductionService.replaceSection6AOwn(currentFy, validS6a);

        // But employee cannot overwrite OFFICER-entered previous employment -> 409 OFFICER_ENTERED
        assertThatThrownBy(() -> deductionService.replacePrevEmploymentOwn(currentFy, prevEmp))
                .isInstanceOf(DeclarationNotEditableException.class)
                .satisfies(e -> assertThat(((DeclarationNotEditableException) e).reasonCode())
                        .isEqualTo("OFFICER_ENTERED"));
    }
}
