package com.infinevo.payroll.tds;

import static com.infinevo.payroll.PayrollTestSchema.TENANT_A;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.infinevo.core.employee.EmployeeService;
import com.infinevo.payroll.PayrollTestApp;
import com.infinevo.payroll.PayrollTestSchema;
import com.infinevo.payroll.payrun.PayRunTestSchema;
import com.infinevo.payroll.taxdeclaration.TaxDeclarationTestSchema;
import com.infinevo.payroll.taxdeclaration.dto.ApiResponse;
import com.infinevo.payroll.tds.dto.EmployeeTdsResponse;
import com.infinevo.payroll.tds.dto.RecordTdsRequest;
import com.infinevo.payroll.tds.exception.EmployeeTdsNotFoundException;
import com.infinevo.payroll.tds.exception.EmployeeTdsValidationException;
import com.infinevo.shared.tenant.TenantContext;
import com.infinevo.shared.test.AbstractIntegrationTest;
import com.infinevo.shared.test.EnabledIfDockerAvailable;
import java.math.BigDecimal;
import java.sql.SQLException;
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
 * Integration test for employee TDS records (W-36.1 §7).
 *
 * <p>Covers:
 * <ul>
 *   <li>PUT twice leaves two rows: one active (superseded_at null), one inactive (superseded_at set)</li>
 *   <li>GET returns the active record</li>
 *   <li>GET .../history returns all rows newest first</li>
 *   <li>GET /me/tds/{fy} for an employee without a record returns 404</li>
 * </ul>
 */
@SpringBootTest(classes = PayrollTestApp.class)
@EnabledIfDockerAvailable
class EmployeeTdsIT extends AbstractIntegrationTest {

    private static final String FY = "2026-2027";

    @Autowired
    private EmployeeTdsService tdsService;

    @Autowired
    private EmployeeService employeeService;

    private EmployeeTdsController controller;

    private UUID employeeId1;
    private UUID employeeId2;

    @BeforeAll
    static void applySchema() throws Exception {
        PayRunTestSchema.apply();
    }

    @AfterAll
    static void cleanUp() throws SQLException {
        PayrollTestSchema.cleanTables();
    }

    @BeforeEach
    void setUp() throws Exception {
        TenantContext.clear();
        PayrollTestSchema.cleanTables();
        PayrollTestSchema.seedTenants();

        employeeId1 = TaxDeclarationTestSchema.seedEmployee(TENANT_A, "EMP-TDS-01", "asha@acme.com", "Asha", "Rao");
        employeeId2 = TaxDeclarationTestSchema.seedEmployee(TENANT_A, "EMP-TDS-02", "bob@acme.com", "Bob", "Smith");

        TenantContext.set(TENANT_A);
        controller = new EmployeeTdsController(tdsService, employeeService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        PayrollTestApp.CURRENT_EMPLOYEE.remove();
    }

    @Test
    @DisplayName("PUT twice leaves two rows in history: one active with superseded_at null and one superseded")
    void putTwiceSupersedesFirst() {
        RecordTdsRequest first = new RecordTdsRequest(
                "NEW",
                new BigDecimal("1200000.00"),
                new BigDecimal("1000000.00"),
                new BigDecimal("120000.00"),
                "2026-04",
                "initial figure");

        ApiResponse<EmployeeTdsResponse> resp1 = controller.put(employeeId1, FY, first);
        assertThat(resp1.status()).isEqualTo(200);
        assertThat(resp1.data().annualTax()).isEqualByComparingTo("120000.0000");
        assertThat(resp1.data().isActive()).isTrue();
        assertThat(resp1.data().supersededAt()).isNull();

        RecordTdsRequest second = new RecordTdsRequest(
                "NEW",
                new BigDecimal("1500000.00"),
                new BigDecimal("1300000.00"),
                new BigDecimal("150000.00"),
                "2026-04",
                "salary increment revision");

        ApiResponse<EmployeeTdsResponse> resp2 = controller.put(employeeId1, FY, second);
        assertThat(resp2.status()).isEqualTo(200);
        assertThat(resp2.data().annualTax()).isEqualByComparingTo("150000.0000");
        assertThat(resp2.data().isActive()).isTrue();
        assertThat(resp2.data().supersededAt()).isNull();

        List<EmployeeTds> rows = tdsService.history(employeeId1, FY);
        assertThat(rows).hasSize(2);

        EmployeeTds activeRow = rows.get(0);
        EmployeeTds supersededRow = rows.get(1);

        assertThat(activeRow.isActive()).isTrue();
        assertThat(activeRow.getSupersededAt()).isNull();
        assertThat(activeRow.getAnnualTax()).isEqualByComparingTo("150000.0000");
        assertThat(activeRow.getNote()).isEqualTo("salary increment revision");

        assertThat(supersededRow.isActive()).isFalse();
        assertThat(supersededRow.getSupersededAt()).isNotNull();
        assertThat(supersededRow.getAnnualTax()).isEqualByComparingTo("120000.0000");
        assertThat(supersededRow.getNote()).isEqualTo("initial figure");
    }

    @Test
    @DisplayName("GET returns the active record with year_to_date and remaining")
    void getReturnsActiveRecord() {
        controller.put(
                employeeId1,
                FY,
                new RecordTdsRequest(
                        "NEW",
                        new BigDecimal("1200000.00"),
                        new BigDecimal("1000000.00"),
                        new BigDecimal("120000.00"),
                        "2026-04",
                        "v1"));

        controller.put(
                employeeId1,
                FY,
                new RecordTdsRequest(
                        "OLD",
                        new BigDecimal("1200000.00"),
                        new BigDecimal("1000000.00"),
                        new BigDecimal("140000.00"),
                        "2026-04",
                        "v2"));

        ApiResponse<EmployeeTdsResponse> getResp = controller.get(employeeId1, FY);
        assertThat(getResp.status()).isEqualTo(200);
        assertThat(getResp.data().regime()).isEqualTo("OLD");
        assertThat(getResp.data().annualTax()).isEqualByComparingTo("140000.0000");
        assertThat(getResp.data().isActive()).isTrue();
        assertThat(getResp.data().supersededAt()).isNull();
        assertThat(getResp.data().yearToDate()).isEqualByComparingTo("0");
        assertThat(getResp.data().remaining()).isEqualByComparingTo("140000.0000");
    }

    @Test
    @DisplayName("GET .../history returns all rows, newest first")
    void historyReturnsNewestFirst() {
        controller.put(
                employeeId1,
                FY,
                new RecordTdsRequest(
                        "NEW",
                        new BigDecimal("1000000.00"),
                        new BigDecimal("900000.00"),
                        new BigDecimal("100000.00"),
                        "2026-04",
                        "first"));

        controller.put(
                employeeId1,
                FY,
                new RecordTdsRequest(
                        "NEW",
                        new BigDecimal("1200000.00"),
                        new BigDecimal("1000000.00"),
                        new BigDecimal("120000.00"),
                        "2026-04",
                        "second"));

        ApiResponse<List<EmployeeTdsResponse>> historyResp = controller.history(employeeId1, FY);
        assertThat(historyResp.status()).isEqualTo(200);
        assertThat(historyResp.data()).hasSize(2);
        assertThat(historyResp.data().get(0).annualTax()).isEqualByComparingTo("120000.0000");
        assertThat(historyResp.data().get(0).note()).isEqualTo("second");
        assertThat(historyResp.data().get(1).annualTax()).isEqualByComparingTo("100000.0000");
        assertThat(historyResp.data().get(1).note()).isEqualTo("first");
    }

    @Test
    @DisplayName("GET /me/tds/{fy} returns authenticated employee's row; another employee gets 404")
    void getOwnReturnsActiveRowOrNotFound() {
        controller.put(
                employeeId1,
                FY,
                new RecordTdsRequest(
                        "NEW",
                        new BigDecimal("1200000.00"),
                        new BigDecimal("1000000.00"),
                        new BigDecimal("120000.00"),
                        "2026-04",
                        "for emp 1"));

        // Login as employee 1
        PayrollTestApp.CURRENT_EMPLOYEE.set(employeeService.get(employeeId1));
        ApiResponse<EmployeeTdsResponse> ownResp = controller.getOwn(FY);
        assertThat(ownResp.status()).isEqualTo(200);
        assertThat(ownResp.data().employeeId()).isEqualTo(employeeId1);
        assertThat(ownResp.data().annualTax()).isEqualByComparingTo("120000.0000");

        // Login as employee 2 (who has no TDS record)
        PayrollTestApp.CURRENT_EMPLOYEE.set(employeeService.get(employeeId2));
        assertThatThrownBy(() -> controller.getOwn(FY)).isInstanceOf(EmployeeTdsNotFoundException.class);
    }

    @Test
    @DisplayName("PUT with a missing annual figure is a validation error (400), not a 500")
    void putWithMissingFigureIsValidationError() {
        assertThatThrownBy(() -> controller.put(
                        employeeId1,
                        FY,
                        new RecordTdsRequest(
                                "NEW",
                                new BigDecimal("1200000.00"),
                                new BigDecimal("1000000.00"),
                                null,
                                "2026-04",
                                null)))
                .isInstanceOf(EmployeeTdsValidationException.class);
    }
}
