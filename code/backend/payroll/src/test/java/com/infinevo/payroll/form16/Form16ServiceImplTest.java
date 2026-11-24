package com.infinevo.payroll.form16;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.core.employee.detail.EmployeeIdentificationService;
import com.infinevo.core.org.DesignationResponse;
import com.infinevo.core.org.DesignationService;
import com.infinevo.core.tenant.TenantBranding;
import com.infinevo.core.tenant.TenantProfileService;
import com.infinevo.payroll.payrun.EmployeePayRunLineRepository;
import com.infinevo.payroll.payrun.PayRunRepository;
import com.infinevo.payroll.priorpayroll.PriorPayrollTaxQuery;
import com.infinevo.payroll.taxcalc.recalc.TaxComputationRepository;
import com.infinevo.payroll.taxdeductor.TaxDeductorResponse;
import com.infinevo.payroll.taxdeductor.TaxDeductorService;
import com.infinevo.payroll.tds.EmployeeTds;
import com.infinevo.payroll.tds.EmployeeTdsService;
import com.infinevo.shared.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** D-149: who the statement names — the tenant as deductor, the designation by name. */
class Form16ServiceImplTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID EMPLOYEE = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID DESIGNATION = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final String FY = "2026-2027";

    private final TaxDeductorService taxDeductorService = mock(TaxDeductorService.class);
    private final EmployeeTdsService employeeTdsService = mock(EmployeeTdsService.class);
    private final EmployeeService employeeService = mock(EmployeeService.class);
    private final TenantProfileService tenantProfileService = mock(TenantProfileService.class);
    private final DesignationService designationService = mock(DesignationService.class);
    private final EmployeeResponse employee = mock(EmployeeResponse.class);

    private Form16ServiceImpl service;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT);
        when(taxDeductorService.current()).thenReturn(Optional.of(mock(TaxDeductorResponse.class)));
        when(employee.firstName()).thenReturn("Asha");
        when(employee.lastName()).thenReturn("Rao");
        when(employeeService.get(EMPLOYEE)).thenReturn(employee);
        EmployeeTds tds = mock(EmployeeTds.class);
        when(tds.getAnnualTax()).thenReturn(new BigDecimal("120000.00"));
        when(employeeTdsService.active(EMPLOYEE, FY)).thenReturn(Optional.of(tds));

        service = new Form16ServiceImpl(
                taxDeductorService,
                employeeTdsService,
                mock(TaxComputationRepository.class),
                mock(EmployeePayRunLineRepository.class),
                mock(PayRunRepository.class),
                employeeService,
                mock(EmployeeIdentificationService.class),
                mock(PriorPayrollTaxQuery.class),
                tenantProfileService,
                designationService);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("The deductor is the tenant by name and the designation prints by name")
    void namesTheTenantAndTheDesignation() {
        when(tenantProfileService.branding())
                .thenReturn(Optional.of(new TenantBranding("Acme Manufacturing", null, null, null, null)));
        when(employee.designationId()).thenReturn(DESIGNATION);
        when(designationService.list(false))
                .thenReturn(List.of(new DesignationResponse(
                        DESIGNATION, TENANT, "SE", "Senior Engineer", true, Instant.now(), Instant.now())));

        Form16Statement statement = service.render(EMPLOYEE, FY);

        assertThat(statement.deductor().name()).isEqualTo("Acme Manufacturing");
        assertThat(statement.employee().name()).isEqualTo("Asha Rao");
        assertThat(statement.employee().designation()).isEqualTo("Senior Engineer");
    }

    @Test
    @DisplayName("A designation that is gone, or none at all, prints blank and never an id")
    void missingDesignationPrintsBlank() {
        when(tenantProfileService.branding()).thenReturn(Optional.empty());
        when(employee.designationId()).thenReturn(DESIGNATION);
        when(designationService.list(false)).thenReturn(List.of());

        Form16Statement gone = service.render(EMPLOYEE, FY);
        assertThat(gone.employee().designation()).isEmpty();
        assertThat(gone.deductor().name()).isEmpty();

        when(employee.designationId()).thenReturn(null);
        assertThat(service.render(EMPLOYEE, FY).employee().designation()).isEmpty();
    }
}
