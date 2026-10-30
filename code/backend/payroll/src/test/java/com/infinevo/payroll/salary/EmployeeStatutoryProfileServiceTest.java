package com.infinevo.payroll.salary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.EmployeeService;
import com.infinevo.shared.tenant.TenantContext;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W-75.1 §7 (was D-132) — the statutory profile save trims each number, stores blank as null, and refuses a number
 * longer than its column as a field error before it can reach the database.
 */
class EmployeeStatutoryProfileServiceTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID EMPLOYEE = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private EmployeeStatutoryProfileRepository repository;
    private EmployeeStatutoryProfileServiceImpl service;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT);
        repository = mock(EmployeeStatutoryProfileRepository.class);
        when(repository.findByTenantIdAndEmployeeId(TENANT, EMPLOYEE)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        service = new EmployeeStatutoryProfileServiceImpl(repository, mock(EmployeeService.class));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("UAN of 16 characters saves; 17 is a field error on uan and nothing is saved")
    void uanLength() {
        assertThat(service.upsert(EMPLOYEE, request(null, "1".repeat(16), null)).uan())
                .hasSize(16);

        assertThatThrownBy(() -> service.upsert(EMPLOYEE, request(null, "1".repeat(17), null)))
                .isInstanceOfSatisfying(SalaryValidationException.class, e -> assertThat(e.getFieldErrors())
                        .containsOnly(entry("uan", "UAN must be at most 16 characters")));
    }

    @Test
    @DisplayName("PF account number and ESI number of 32 characters save; 33 is a field error on that field")
    void pfAndEsiLength() {
        StatutoryProfileResponse saved = service.upsert(EMPLOYEE, request("P".repeat(32), null, "E".repeat(32)));
        assertThat(saved.pfAccountNumber()).hasSize(32);
        assertThat(saved.esiNumber()).hasSize(32);

        assertThatThrownBy(() -> service.upsert(EMPLOYEE, request("P".repeat(33), null, null)))
                .isInstanceOfSatisfying(SalaryValidationException.class, e -> assertThat(e.getFieldErrors())
                        .containsOnlyKeys("pfAccountNumber"));
        assertThatThrownBy(() -> service.upsert(EMPLOYEE, request(null, null, "E".repeat(33))))
                .isInstanceOfSatisfying(SalaryValidationException.class, e -> assertThat(e.getFieldErrors())
                        .containsOnlyKeys("esiNumber"));
    }

    @Test
    @DisplayName("An over-long number is refused before save")
    void refusedBeforeSave() {
        StatutoryProfileRequest tooLong = request(null, "1".repeat(17), null);
        assertThatThrownBy(() -> service.upsert(EMPLOYEE, tooLong)).isInstanceOf(SalaryValidationException.class);
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Whitespace is trimmed before the length check and before saving")
    void whitespaceTrimmed() {
        StatutoryProfileResponse saved =
                service.upsert(EMPLOYEE, request("  MH/BAN/123  ", "  " + "1".repeat(16) + "  ", "\t3100123\n"));

        assertThat(saved.pfAccountNumber()).isEqualTo("MH/BAN/123");
        assertThat(saved.uan()).isEqualTo("1".repeat(16));
        assertThat(saved.esiNumber()).isEqualTo("3100123");
    }

    @Test
    @DisplayName("A blank number is stored as null")
    void blankBecomesNull() {
        StatutoryProfileResponse saved = service.upsert(EMPLOYEE, request("   ", "", "\t"));

        assertThat(saved.pfAccountNumber()).isNull();
        assertThat(saved.uan()).isNull();
        assertThat(saved.esiNumber()).isNull();
    }

    private static StatutoryProfileRequest request(String pfAccountNumber, String uan, String esiNumber) {
        return new StatutoryProfileRequest(
                true, false, false, true, true, false, false, pfAccountNumber, uan, esiNumber);
    }
}
