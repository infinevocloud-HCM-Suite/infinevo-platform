package com.infinevo.payroll.taxdeductor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.EmployeeResponse;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.shared.tenant.TenantContext;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for tax deductor validation rules (W-36.3, spec section 7).
 * No Spring context as required by CONVENTIONS.md section 3.
 */
class TaxDeductorRulesTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private TaxDeductorRepository repository;
    private EmployeeService employeeService;
    private TaxDeductorServiceImpl service;

    private TaxDeductor storedEntity;

    @BeforeEach
    void setUp() {
        repository = mock(TaxDeductorRepository.class);
        employeeService = mock(EmployeeService.class);
        service = new TaxDeductorServiceImpl(repository, employeeService);

        storedEntity = null;
        when(repository.findByTenantId(TENANT_ID)).thenAnswer(inv -> Optional.ofNullable(storedEntity));
        when(repository.saveAndFlush(any(TaxDeductor.class))).thenAnswer(inv -> {
            TaxDeductor d = inv.getArgument(0);
            storedEntity = d;
            return d;
        });

        TenantContext.set(TENANT_ID);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private TaxDeductorRequest validRequest() {
        return new TaxDeductorRequest(
                "MUMT12345A", "ABCDE1234F", "MUM/TD/001/01", null, "Rajesh Sharma", "Ramesh Sharma", "Head of Payroll");
    }

    @Test
    @DisplayName("A lower-case TAN is uppercased and accepted")
    void lowerCaseTan_isUppercasedAndAccepted() {
        TaxDeductorRequest req = new TaxDeductorRequest(
                "mumt12345a", "ABCDE1234F", "MUM/TD/001/01", null, "Rajesh Sharma", "Ramesh Sharma", "Head of Payroll");

        TaxDeductorResponse response = service.save(req);

        assertThat(response).isNotNull();
        assertThat(response.tan()).isEqualTo("MUMT12345A");
        assertThat(storedEntity.getTan()).isEqualTo("MUMT12345A");
    }

    @Test
    @DisplayName("ABCD1234E (9 chars) is refused as an invalid TAN")
    void tanNineChars_isRefused() {
        TaxDeductorRequest req = new TaxDeductorRequest(
                "ABCD1234E", "ABCDE1234F", "MUM/TD/001/01", null, "Rajesh Sharma", "Ramesh Sharma", "Head of Payroll");

        assertThatThrownBy(() -> service.save(req))
                .isInstanceOf(TaxDeductorValidationException.class)
                .hasMessageContaining("Invalid TAN format");
    }

    @Test
    @DisplayName("A PAN in TAN shape (5 letters, 4 digits, 1 letter) is refused as a TAN")
    void panInTanShape_isRefusedAsTan() {
        TaxDeductorRequest req = new TaxDeductorRequest(
                "ABCDE1234F", // PAN format in TAN field
                "ABCDE1234F",
                "MUM/TD/001/01",
                null,
                "Rajesh Sharma",
                "Ramesh Sharma",
                "Head of Payroll");

        assertThatThrownBy(() -> service.save(req))
                .isInstanceOf(TaxDeductorValidationException.class)
                .hasMessageContaining("Invalid TAN format");
    }

    @Test
    @DisplayName("A TAN in PAN shape (4 letters, 5 digits, 1 letter) is refused as a PAN")
    void tanInPanShape_isRefusedAsPan() {
        TaxDeductorRequest req = new TaxDeductorRequest(
                "MUMT12345A",
                "MUMT12345A", // TAN format in PAN field
                "MUM/TD/001/01",
                null,
                "Rajesh Sharma",
                "Ramesh Sharma",
                "Head of Payroll");

        assertThatThrownBy(() -> service.save(req))
                .isInstanceOf(TaxDeductorValidationException.class)
                .hasMessageContaining("Invalid PAN format");
    }

    @Test
    @DisplayName("A lower-case PAN is uppercased and accepted")
    void lowerCasePan_isUppercasedAndAccepted() {
        TaxDeductorRequest req = new TaxDeductorRequest(
                "MUMT12345A", "abcde1234f", "MUM/TD/001/01", null, "Rajesh Sharma", "Ramesh Sharma", "Head of Payroll");

        TaxDeductorResponse response = service.save(req);

        assertThat(response.pan()).isEqualTo("ABCDE1234F");
        assertThat(storedEntity.getPan()).isEqualTo("ABCDE1234F");
    }

    @Test
    @DisplayName("TDS circle MUM/TD/001/01 passes and MUM-TD-001 fails")
    void tdsCircleValidation() {
        // Valid circle
        TaxDeductorRequest valid = new TaxDeductorRequest(
                "MUMT12345A", "ABCDE1234F", "MUM/TD/001/01", null, "Rajesh Sharma", null, "Head of Payroll");
        TaxDeductorResponse response = service.save(valid);
        assertThat(response.tdsCircle()).isEqualTo("MUM/TD/001/01");

        // Invalid circle
        TaxDeductorRequest invalid = new TaxDeductorRequest(
                "MUMT12345A", "ABCDE1234F", "MUM-TD-001", null, "Rajesh Sharma", null, "Head of Payroll");
        assertThatThrownBy(() -> service.save(invalid))
                .isInstanceOf(TaxDeductorValidationException.class)
                .hasMessageContaining("Invalid TDS circle format");
    }

    @Test
    @DisplayName("Null or blank TDS circle is accepted and stored as null")
    void nullOrBlankTdsCircle_isAccepted() {
        TaxDeductorRequest req =
                new TaxDeductorRequest("MUMT12345A", "ABCDE1234F", "", null, "Rajesh Sharma", null, "Head of Payroll");

        TaxDeductorResponse response = service.save(req);
        assertThat(response.tdsCircle()).isNull();
    }

    @Test
    @DisplayName("A blank signatory name is refused")
    void blankSignatoryName_isRefused() {
        TaxDeductorRequest req = new TaxDeductorRequest(
                "MUMT12345A", "ABCDE1234F", "MUM/TD/001/01", null, "   ", "Ramesh Sharma", "Head of Payroll");

        assertThatThrownBy(() -> service.save(req))
                .isInstanceOf(TaxDeductorValidationException.class)
                .hasMessageContaining("Signatory name is required");
    }

    @Test
    @DisplayName("A signatory name exceeding 120 chars is refused")
    void signatoryNameTooLong_isRefused() {
        String longName = "A".repeat(121);
        TaxDeductorRequest req = new TaxDeductorRequest(
                "MUMT12345A", "ABCDE1234F", "MUM/TD/001/01", null, longName, "Ramesh Sharma", "Head of Payroll");

        assertThatThrownBy(() -> service.save(req))
                .isInstanceOf(TaxDeductorValidationException.class)
                .hasMessageContaining("Signatory name must be between 1 and 120 characters");
    }

    @Test
    @DisplayName("A blank signatory designation is refused")
    void blankSignatoryDesignation_isRefused() {
        TaxDeductorRequest req = new TaxDeductorRequest(
                "MUMT12345A", "ABCDE1234F", "MUM/TD/001/01", null, "Rajesh Sharma", "Ramesh Sharma", "");

        assertThatThrownBy(() -> service.save(req))
                .isInstanceOf(TaxDeductorValidationException.class)
                .hasMessageContaining("Signatory designation is required");
    }

    @Test
    @DisplayName("A signatory designation exceeding 120 chars is refused")
    void signatoryDesignationTooLong_isRefused() {
        String longDesignation = "D".repeat(121);
        TaxDeductorRequest req = new TaxDeductorRequest(
                "MUMT12345A", "ABCDE1234F", "MUM/TD/001/01", null, "Rajesh Sharma", "Ramesh Sharma", longDesignation);

        assertThatThrownBy(() -> service.save(req))
                .isInstanceOf(TaxDeductorValidationException.class)
                .hasMessageContaining("Signatory designation must be between 1 and 120 characters");
    }

    @Test
    @DisplayName("A signatory parent name exceeding 120 chars is refused")
    void signatoryParentNameTooLong_isRefused() {
        String longParent = "P".repeat(121);
        TaxDeductorRequest req = new TaxDeductorRequest(
                "MUMT12345A", "ABCDE1234F", "MUM/TD/001/01", null, "Rajesh Sharma", longParent, "Head of Payroll");

        assertThatThrownBy(() -> service.save(req))
                .isInstanceOf(TaxDeductorValidationException.class)
                .hasMessageContaining("Signatory parent name must not exceed 120 characters");
    }

    @Test
    @DisplayName("A signatory employee that does not exist in bound tenant is refused with 400 validation error")
    void unknownSignatoryEmployee_isRefused() {
        UUID unknownEmpId = UUID.randomUUID();
        when(employeeService.get(unknownEmpId)).thenThrow(new EmployeeService.NotFoundException(unknownEmpId));

        TaxDeductorRequest req = new TaxDeductorRequest(
                "MUMT12345A",
                "ABCDE1234F",
                "MUM/TD/001/01",
                unknownEmpId,
                "Rajesh Sharma",
                "Ramesh Sharma",
                "Head of Payroll");

        assertThatThrownBy(() -> service.save(req))
                .isInstanceOf(TaxDeductorValidationException.class)
                .hasMessageContaining("Signatory employee not found in bound tenant");
    }

    @Test
    @DisplayName("A live signatory employee in bound tenant is accepted")
    void liveSignatoryEmployee_isAccepted() {
        UUID empId = UUID.randomUUID();
        EmployeeResponse emp = new EmployeeResponse(
                empId,
                TENANT_ID,
                "EMP001",
                "Rajesh",
                null,
                "Sharma",
                "MALE",
                LocalDate.now(),
                null,
                null,
                "rajesh@example.com",
                null,
                false,
                null,
                null,
                null,
                null,
                Instant.now(),
                Instant.now());
        when(employeeService.get(empId)).thenReturn(emp);

        TaxDeductorRequest req = new TaxDeductorRequest(
                "MUMT12345A",
                "ABCDE1234F",
                "MUM/TD/001/01",
                empId,
                "Rajesh Sharma",
                "Ramesh Sharma",
                "Head of Payroll");

        TaxDeductorResponse response = service.save(req);
        assertThat(response.signatoryEmployeeId()).isEqualTo(empId);
        assertThat(storedEntity.getSignatoryEmployeeId()).isEqualTo(empId);
    }

    @Test
    @DisplayName("Second save updates the existing row and does not duplicate it")
    void secondSaveUpdatesExistingRow() {
        service.save(validRequest());
        assertThat(storedEntity).isNotNull();
        assertThat(storedEntity.getSignatoryName()).isEqualTo("Rajesh Sharma");

        TaxDeductorRequest updatedReq = new TaxDeductorRequest(
                "MUMT12345A", "ABCDE1234F", "MUM/TD/002/02", null, "Sunil Varma", "Anil Varma", "VP Finance");

        TaxDeductorResponse updated = service.save(updatedReq);
        assertThat(updated.signatoryName()).isEqualTo("Sunil Varma");
        assertThat(updated.tdsCircle()).isEqualTo("MUM/TD/002/02");
        assertThat(storedEntity.getSignatoryName()).isEqualTo("Sunil Varma");
    }
}
