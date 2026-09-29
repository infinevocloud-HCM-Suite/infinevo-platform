package com.infinevo.payroll.taxdeclaration.housing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.core.employee.EmployeeService;
import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.taxdeclaration.EmployeeInvestmentDeclaration;
import com.infinevo.payroll.taxdeclaration.FinancialYear;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationService;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationTestSchema;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationWindowService;
import com.infinevo.payroll.taxdeclaration.dto.TaxDeclarationWindowRequest;
import com.infinevo.payroll.taxdeclaration.exception.DeclarationNotEditableException;
import com.infinevo.payroll.taxdeclaration.exception.WindowValidationException;
import com.infinevo.payroll.taxdeclaration.housing.dto.HomeLoanRequest;
import com.infinevo.payroll.taxdeclaration.housing.dto.HouseRentRequest;
import com.infinevo.payroll.taxdeclaration.housing.dto.HousingDeclarationResponse;
import com.infinevo.payroll.taxdeclaration.housing.dto.LetOutPropertyLineRequest;
import com.infinevo.payroll.taxdeclaration.housing.dto.LetOutPropertyRequest;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Collections;
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
 * Acceptance integration test for housing declarations (W-32.2).
 */
@SpringBootTest(classes = PayrollTestApp.class)
class HousingDeclarationIT extends AbstractIntegrationTest {

    @Autowired
    private TaxDeclarationWindowService windowService;

    @Autowired
    private TaxDeclarationService taxDeclarationService;

    @Autowired
    private HousingDeclarationService housingDeclarationService;

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
                TaxDeclarationTestSchema.TENANT_A, "EMP-101", "housing.emp@acme.com", "Housing", "Tester");

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
            "Complete housing declaration flow: rent, loan, let-out property, calculations, header sync, and lock guard")
    void testHousingDeclarationLifecycle() {
        FinancialYear fy = FinancialYear.parse(currentFy);
        int startYear = fy.start().getYear();
        int endYear = fy.end().getYear();

        String aprMonth = String.format("%04d-04", startYear);
        String sepMonth = String.format("%04d-09", startYear);
        String octMonth = String.format("%04d-10", startYear);
        String marMonth = String.format("%04d-03", endYear);

        // 1. Initial read returns empty lists
        taxDeclarationService.readOwn(currentFy);
        HousingDeclarationResponse initial = housingDeclarationService.readOwn(currentFy);
        assertThat(initial.houseRent()).isEmpty();
        assertThat(initial.homeLoans()).isEmpty();
        assertThat(initial.letOutProperties()).isEmpty();

        // 2. Declare house rent (2 periods, metro and non-metro)
        List<HouseRentRequest> rentRequests = List.of(
                new HouseRentRequest(
                        aprMonth,
                        sepMonth,
                        "Flat 101, Bandra, Mumbai",
                        "Landlord Ramesh",
                        "ABCDE1234F",
                        true,
                        new BigDecimal("25000.0000")),
                new HouseRentRequest(
                        octMonth,
                        marMonth,
                        "House 42, Sector 15, Gurgaon",
                        "Landlord Suresh",
                        "XYZPK9876Q",
                        false,
                        new BigDecimal("22000.0000")));

        HousingDeclarationResponse rentResp = housingDeclarationService.replaceHouseRentOwn(currentFy, rentRequests);
        assertThat(rentResp.houseRent()).hasSize(2);
        assertThat(rentResp.houseRent().get(0).landlordName()).isEqualTo("Landlord Ramesh");
        assertThat(rentResp.houseRent().get(0).isMetro()).isTrue();
        assertThat(rentResp.houseRent().get(1).landlordName()).isEqualTo("Landlord Suresh");

        // Verify header flag updated
        EmployeeInvestmentDeclaration headerAfterRent = taxDeclarationService.require(employeeId, currentFy);
        assertThat(headerAfterRent.isStayingInRentedHouse()).isTrue();

        // 3. Declare self-occupied home loan
        List<HomeLoanRequest> loanRequests = List.of(new HomeLoanRequest(
                "State Bank of India",
                "AAACS1234F",
                new BigDecimal("150000.0000"),
                new BigDecimal("200000.0000"),
                true,
                LocalDate.of(2022, 6, 15)));

        HousingDeclarationResponse loanResp = housingDeclarationService.replaceHomeLoansOwn(currentFy, loanRequests);
        assertThat(loanResp.homeLoans()).hasSize(1);
        assertThat(loanResp.homeLoans().get(0).lenderName()).isEqualTo("State Bank of India");
        assertThat(loanResp.homeLoans().get(0).principalPaid()).isEqualByComparingTo(new BigDecimal("150000.0000"));
        assertThat(loanResp.homeLoans().get(0).interestPaid()).isEqualByComparingTo(new BigDecimal("200000.0000"));
        assertThat(loanResp.homeLoans().get(0).isFirstTimeBuyer()).isTrue();

        // Verify header flag updated
        EmployeeInvestmentDeclaration headerAfterLoan = taxDeclarationService.require(employeeId, currentFy);
        assertThat(headerAfterLoan.isRepayingSelfOccupiedLoan()).isTrue();

        // 4. Declare let-out property with lines and check net income loss calculation
        // Annual rent = 3,00,000, Municipal tax = 20,000, Loan interest = 3,00,000
        // Net loss = -1,04,000.0000
        List<LetOutPropertyRequest> propRequests = List.of(new LetOutPropertyRequest(
                "Palm Meadows Villa 5",
                "Whitefield, Bengaluru",
                List.of(
                        new LetOutPropertyLineRequest(
                                LetOutPropertyLineType.ANNUAL_RENT, new BigDecimal("300000.0000"), null, null),
                        new LetOutPropertyLineRequest(
                                LetOutPropertyLineType.MUNICIPAL_TAX, new BigDecimal("20000.0000"), null, null),
                        new LetOutPropertyLineRequest(
                                LetOutPropertyLineType.LOAN_INTEREST,
                                new BigDecimal("300000.0000"),
                                "ICICI Bank",
                                "AAACI1234F"))));

        HousingDeclarationResponse propResp =
                housingDeclarationService.replaceLetOutPropertiesOwn(currentFy, propRequests);
        assertThat(propResp.letOutProperties()).hasSize(1);
        assertThat(propResp.letOutProperties().get(0).propertyName()).isEqualTo("Palm Meadows Villa 5");
        assertThat(propResp.letOutProperties().get(0).netIncomeLoss())
                .isEqualByComparingTo(new BigDecimal("-104000.0000"));
        assertThat(propResp.letOutProperties().get(0).lines()).hasSize(3);

        // Verify header flag updated
        EmployeeInvestmentDeclaration headerAfterProp = taxDeclarationService.require(employeeId, currentFy);
        assertThat(headerAfterProp.hasLetOutProperty()).isTrue();

        // 5. Submit declaration -> editing housing sections is blocked
        taxDeclarationService.submitOwn(currentFy);

        assertThatThrownBy(() -> housingDeclarationService.replaceHouseRentOwn(currentFy, rentRequests))
                .isInstanceOf(DeclarationNotEditableException.class)
                .satisfies(e -> assertThat(((DeclarationNotEditableException) e).reasonCode())
                        .isEqualTo("NOT_EDITABLE"));
        assertThatThrownBy(() -> housingDeclarationService.replaceHomeLoansOwn(currentFy, loanRequests))
                .isInstanceOf(DeclarationNotEditableException.class)
                .satisfies(e -> assertThat(((DeclarationNotEditableException) e).reasonCode())
                        .isEqualTo("NOT_EDITABLE"));
        assertThatThrownBy(() -> housingDeclarationService.replaceLetOutPropertiesOwn(currentFy, propRequests))
                .isInstanceOf(DeclarationNotEditableException.class)
                .satisfies(e -> assertThat(((DeclarationNotEditableException) e).reasonCode())
                        .isEqualTo("NOT_EDITABLE"));

        // 6. Officer can update housing declarations after reopening
        taxDeclarationService.reopen(employeeId, currentFy);

        // Verify officer PUT succeeds when window is closed (D-8 item 1)
        LocalDate openDate = LocalDate.now().minusDays(10);
        LocalDate closeDate = LocalDate.now().plusDays(20);
        TaxDeclarationWindowRequest lockedWindow = new TaxDeclarationWindowRequest(
                openDate, closeDate, true /* isLocked */, "NEW", true, true, false, false);
        windowService.upsert(currentFy, lockedWindow);

        // Employee PUT is rejected with 409 NOT_EDITABLE (spec §4); the message says the window is closed
        assertThatThrownBy(() -> housingDeclarationService.replaceHomeLoansOwn(currentFy, loanRequests))
                .isInstanceOf(DeclarationNotEditableException.class)
                .hasMessageContaining("window is closed")
                .satisfies(e -> assertThat(((DeclarationNotEditableException) e).reasonCode())
                        .isEqualTo("NOT_EDITABLE"));

        // Officer PUT with window closed still succeeds (officer bypasses window)
        HousingDeclarationResponse officerResp =
                housingDeclarationService.replaceHomeLoans(employeeId, currentFy, loanRequests);
        assertThat(officerResp.homeLoans()).hasSize(1);

        // Restore unlocked window
        TaxDeclarationWindowRequest unlockedWindow = new TaxDeclarationWindowRequest(
                openDate, closeDate, false /* isLocked */, "NEW", true, true, false, false);
        windowService.upsert(currentFy, unlockedWindow);

        // 7. Employee reopens and clears house rent -> header flag is reset
        taxDeclarationService.reopenOwn(currentFy);
        HousingDeclarationResponse clearedRentResp =
                housingDeclarationService.replaceHouseRentOwn(currentFy, Collections.emptyList());
        assertThat(clearedRentResp.houseRent()).isEmpty();

        EmployeeInvestmentDeclaration headerAfterClear = taxDeclarationService.require(employeeId, currentFy);
        assertThat(headerAfterClear.isStayingInRentedHouse()).isFalse();
    }

    @Test
    @DisplayName("A NEW-regime header (the tenant default) saves house rent and let-out property without error")
    void newRegimeHeaderSavesHousingSections() {
        FinancialYear fy = FinancialYear.parse(currentFy);
        int startYear = fy.start().getYear();

        // The window default is NEW, so the header created on first read is NEW. The seed carries the
        // HRA and let-out rules under OLD only; both saves used to fail with a 500 on this path.
        LocalDate openDate = LocalDate.now().minusDays(1);
        LocalDate closeDate = LocalDate.now().plusDays(30);
        if (openDate.isBefore(fy.start())) openDate = fy.start();
        if (closeDate.isAfter(fy.end())) closeDate = fy.end();
        windowService.upsert(
                currentFy,
                new TaxDeclarationWindowRequest(openDate, closeDate, false, "NEW", true, true, false, false));
        assertThat(taxDeclarationService.readOwn(currentFy).taxRegime()).isEqualTo("NEW");

        // Empty list: nothing to check, no reference read at all
        assertThat(housingDeclarationService
                        .replaceHouseRentOwn(currentFy, Collections.emptyList())
                        .houseRent())
                .isEmpty();

        // Rent over the threshold with a PAN: the threshold is read for a NEW header
        HousingDeclarationResponse rent = housingDeclarationService.replaceHouseRentOwn(
                currentFy,
                List.of(new HouseRentRequest(
                        String.format("%04d-04", startYear),
                        String.format("%04d-09", startYear),
                        "Flat 7, Pune",
                        "Landlord Anil",
                        "ABCDE1234F",
                        false,
                        new BigDecimal("20000.0000"))));
        assertThat(rent.houseRent()).hasSize(1);

        // Rent over the threshold without a PAN is still a 400, not a 500
        assertThatThrownBy(() -> housingDeclarationService.replaceHouseRentOwn(
                        currentFy,
                        List.of(new HouseRentRequest(
                                String.format("%04d-04", startYear),
                                String.format("%04d-09", startYear),
                                "Flat 7, Pune",
                                "Landlord Anil",
                                null,
                                false,
                                new BigDecimal("20000.0000")))))
                .isInstanceOf(WindowValidationException.class)
                .hasMessageContaining("Landlord PAN is mandatory");

        // Let-out property: the 30 % standard deduction is read for a NEW header
        HousingDeclarationResponse prop = housingDeclarationService.replaceLetOutPropertiesOwn(
                currentFy,
                List.of(new LetOutPropertyRequest(
                        "Flat 9",
                        "Kochi",
                        List.of(
                                new LetOutPropertyLineRequest(
                                        LetOutPropertyLineType.ANNUAL_RENT, new BigDecimal("100000.0000"), null, null),
                                new LetOutPropertyLineRequest(
                                        LetOutPropertyLineType.MUNICIPAL_TAX,
                                        new BigDecimal("10000.0000"),
                                        null,
                                        null)))));
        // 100000 - 10000 - 30 % of 90000 = 63000
        assertThat(prop.letOutProperties().get(0).netIncomeLoss()).isEqualByComparingTo(new BigDecimal("63000.0000"));
    }
}
