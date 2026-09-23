package com.infinevo.core.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.shared.tenant.TenantContext;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * W-13.1 — validation, "soft delete excludes from read", and the status transitions (spec section 7).
 *
 * <p>The repository is a small in-memory stand-in rather than a set of one-shot stubs. The point of
 * the soft-delete test is that a delete followed by a read finds nothing, and a stub told to return
 * empty proves only that it was told to. Here the store keeps the row, the finder applies the same
 * {@code deleted} and {@code tenantId} predicates the generated query does, and the test fails if
 * {@link EmployeeServiceImpl} ever reads through a path that does not.
 *
 * <p>No Spring context, as {@code docs/CONVENTIONS.md} section 3 requires of a {@code *Test}. Row-level
 * security is not in scope here and cannot be — that is {@link EmployeeRlsIT}, against real Postgres.
 */
class EmployeeServiceImplTest {

    private static final UUID TENANT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TENANT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final LocalDate JOINED = LocalDate.of(2026, 4, 1);

    private final Map<UUID, Employee> store = new LinkedHashMap<>();
    private EmployeeRepository repository;
    private EmployeeServiceImpl service;

    @BeforeEach
    void setUp() {
        store.clear();
        repository = mock(EmployeeRepository.class);
        service = new EmployeeServiceImpl(repository);

        when(repository.saveAndFlush(any(Employee.class))).thenAnswer(inv -> put(inv.getArgument(0)));
        when(repository.save(any(Employee.class))).thenAnswer(inv -> put(inv.getArgument(0)));
        when(repository.findByIdAndTenantIdAndDeletedFalse(any(UUID.class), any(UUID.class)))
                .thenAnswer(inv -> {
                    UUID id = inv.getArgument(0);
                    UUID tenantId = inv.getArgument(1);
                    return Optional.ofNullable(store.get(id))
                            .filter(e -> e.getTenantId().equals(tenantId))
                            .filter(e -> !e.isDeleted());
                });
        when(repository.existsByTenantIdAndEmployeeNumber(any(UUID.class), anyString()))
                .thenAnswer(inv -> store.values().stream()
                        .anyMatch(e -> e.getTenantId().equals(inv.getArgument(0))
                                && e.getEmployeeNumber().equals(inv.getArgument(1))));
        when(repository.existsByTenantIdAndEmployeeNumberAndIdNot(any(UUID.class), anyString(), any(UUID.class)))
                .thenAnswer(inv -> store.values().stream()
                        .anyMatch(e -> e.getTenantId().equals(inv.getArgument(0))
                                && e.getEmployeeNumber().equals(inv.getArgument(1))
                                && !e.getId().equals(inv.getArgument(2))));

        TenantContext.set(TENANT_A);
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    /** Stands in for the database default on {@code id}; Hibernate assigns it in the real thing. */
    private Employee put(Employee employee) {
        if (employee.getId() == null) {
            ReflectionTestUtils.setField(employee, "id", UUID.randomUUID());
        }
        store.put(employee.getId(), employee);
        return employee;
    }

    private static EmployeeRequest request(String employeeNumber) {
        return new EmployeeRequest(employeeNumber, "Asha", null, "Rao", "F", JOINED, null, null, null, null, null);
    }

    // --- the tenant is never the caller's to state -------------------------------------------

    @Test
    @DisplayName("EmployeeRequest carries no tenant field — the tenant cannot be stated by a caller")
    void requestHasNoTenantField() {
        assertThat(EmployeeRequest.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .noneMatch(name -> name.toLowerCase(java.util.Locale.ROOT).contains("tenant"));
    }

    @Test
    @DisplayName("With no tenant bound, nothing can be created or read")
    void unboundTenantIsRefused() {
        TenantContext.clear();

        assertThatThrownBy(() -> service.create(request("E-1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No tenant bound");
        assertThatThrownBy(() -> service.get(UUID.randomUUID())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("An employee of another tenant is not readable, even by id")
    void anotherTenantsEmployeeIsNotReadable() {
        UUID otherId = service.create(request("E-1")).id();
        TenantContext.set(TENANT_B);

        assertThatThrownBy(() -> service.get(otherId)).isInstanceOf(EmployeeService.NotFoundException.class);
    }

    // --- validation ---------------------------------------------------------------------------

    @Test
    @DisplayName("Create returns the stored employee, defaulting status to ACTIVE and the portal to on")
    void createAppliesTheDefaults() {
        EmployeeResponse created = service.create(request("E-1"));

        assertThat(created.id()).isNotNull();
        assertThat(created.tenantId()).isEqualTo(TENANT_A);
        assertThat(created.employeeNumber()).isEqualTo("E-1");
        assertThat(created.status()).isEqualTo(EmploymentStatus.ACTIVE);
        assertThat(created.portalEnabled()).isTrue();
        assertThat(created.dateOfJoining()).isEqualTo(JOINED);
        assertThat(created.terminationDate()).isNull();
    }

    @Test
    @DisplayName("A blank employee number and a missing joining date are both reported, by field")
    void requiredFieldsAreReported() {
        EmployeeRequest bad = new EmployeeRequest("   ", null, null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.create(bad))
                .isInstanceOf(EmployeeService.ValidationException.class)
                .extracting(e -> ((EmployeeService.ValidationException) e).fieldErrors())
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsKeys("employeeNumber", "firstName", "dateOfJoining");
    }

    @Test
    @DisplayName("A work email that is not an address is refused")
    void workEmailIsChecked() {
        EmployeeRequest bad =
                new EmployeeRequest("E-1", "Asha", null, "Rao", null, JOINED, null, null, "asha-at-work", null, null);

        assertThatThrownBy(() -> service.create(bad)).isInstanceOf(EmployeeService.ValidationException.class);
    }

    @Test
    @DisplayName("An employee number longer than the column is refused before it reaches the database")
    void employeeNumberLengthIsChecked() {
        EmployeeRequest bad = request("E".repeat(65));

        assertThatThrownBy(() -> service.create(bad)).isInstanceOf(EmployeeService.ValidationException.class);
    }

    @Test
    @DisplayName("The same employee number twice in one tenant is a conflict, not a stack trace")
    void duplicateEmployeeNumberInOneTenant() {
        service.create(request("E-1"));

        assertThatThrownBy(() -> service.create(request("E-1")))
                .isInstanceOf(EmployeeService.DuplicateEmployeeNumberException.class)
                .hasMessageContaining("E-1")
                .hasMessageContaining("this tenant");
    }

    @Test
    @DisplayName("The same employee number in another tenant is legal — uniqueness is per tenant")
    void sameEmployeeNumberInAnotherTenantIsFine() {
        service.create(request("E-1"));
        TenantContext.set(TENANT_B);

        EmployeeResponse other = service.create(request("E-1"));

        assertThat(other.tenantId()).isEqualTo(TENANT_B);
        assertThat(other.employeeNumber()).isEqualTo("E-1");
    }

    // --- soft delete --------------------------------------------------------------------------

    @Test
    @DisplayName("Delete hides the employee from every read, and does not remove the row")
    void softDeleteExcludesFromRead() {
        UUID id = service.create(request("E-1")).id();

        service.delete(id);

        assertThatThrownBy(() -> service.get(id)).isInstanceOf(EmployeeService.NotFoundException.class);
        assertThat(store).containsKey(id);
        assertThat(store.get(id).isDeleted()).isTrue();
    }

    @Test
    @DisplayName("A deleted employee cannot be updated or deleted again")
    void deletedEmployeeIsGoneForEveryOperation() {
        UUID id = service.create(request("E-1")).id();
        service.delete(id);

        assertThatThrownBy(() -> service.update(id, request("E-1")))
                .isInstanceOf(EmployeeService.NotFoundException.class);
        assertThatThrownBy(() -> service.delete(id)).isInstanceOf(EmployeeService.NotFoundException.class);
    }

    @Test
    @DisplayName("A deleted employee keeps its number — the index covers deleted rows too")
    void aDeletedNumberStaysSpent() {
        UUID id = service.create(request("E-1")).id();
        service.delete(id);

        assertThatThrownBy(() -> service.create(request("E-1")))
                .isInstanceOf(EmployeeService.DuplicateEmployeeNumberException.class);
    }

    @Test
    @DisplayName("Only the employee-number index means duplicate; any other constraint is rethrown")
    void unrelatedConstraintViolationIsNotReportedAsADuplicate() {
        // The catch in save() used to translate EVERY DataIntegrityViolationException into
        // DuplicateEmployeeNumberException. A foreign-key failure on tenant_id — a token naming a
        // tenant with no core.tenant row, which V010__employee.sql:6 allows — was then reported as
        // "employee number E-1 is already in use in this tenant". The cause was swallowed and the
        // message was false. This asserts the real exception now escapes instead.
        DataIntegrityViolationException foreignKeyFailure = new DataIntegrityViolationException(
                "could not execute statement",
                new RuntimeException("ERROR: insert or update on table \"employee\" violates foreign key"
                        + " constraint \"employee_tenant_id_fkey\""));
        when(repository.saveAndFlush(any(Employee.class))).thenThrow(foreignKeyFailure);

        assertThatThrownBy(() -> service.create(request("E-1")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("could not execute statement");
    }

    @Test
    @DisplayName("A violation naming the employee-number index is still reported as a duplicate")
    void theEmployeeNumberIndexIsStillTranslated() {
        DataIntegrityViolationException duplicate = new DataIntegrityViolationException(
                "could not execute statement",
                new RuntimeException("ERROR: duplicate key value violates unique constraint"
                        + " \"idx_employee_tenant_employee_number\""));
        when(repository.saveAndFlush(any(Employee.class))).thenThrow(duplicate);

        assertThatThrownBy(() -> service.create(request("E-1")))
                .isInstanceOf(EmployeeService.DuplicateEmployeeNumberException.class);
    }

    // --- status transitions -------------------------------------------------------------------

    @Test
    @DisplayName("ACTIVE to SUSPENDED and back again")
    void suspendAndReinstate() {
        UUID id = service.create(request("E-1")).id();

        assertThat(service.update(id, withStatus(EmploymentStatus.SUSPENDED, null))
                        .status())
                .isEqualTo(EmploymentStatus.SUSPENDED);
        assertThat(service.update(id, withStatus(EmploymentStatus.ACTIVE, null)).status())
                .isEqualTo(EmploymentStatus.ACTIVE);
    }

    @Test
    @DisplayName("Termination needs a termination date")
    void terminationRequiresADate() {
        UUID id = service.create(request("E-1")).id();

        assertThatThrownBy(() -> service.update(id, withStatus(EmploymentStatus.TERMINATED, null)))
                .isInstanceOf(EmployeeService.ValidationException.class)
                .hasMessageContaining("terminationDate");
    }

    @Test
    @DisplayName("A termination date before the joining date is refused")
    void terminationCannotPrecedeJoining() {
        UUID id = service.create(request("E-1")).id();

        assertThatThrownBy(() -> service.update(id, withStatus(EmploymentStatus.TERMINATED, JOINED.minusDays(1))))
                .isInstanceOf(EmployeeService.ValidationException.class)
                .hasMessageContaining("cannot precede");
    }

    @Test
    @DisplayName("A termination date on a still-employed person is refused")
    void terminationDateWithoutTermination() {
        UUID id = service.create(request("E-1")).id();

        assertThatThrownBy(() -> service.update(id, withStatus(EmploymentStatus.ACTIVE, JOINED.plusDays(30))))
                .isInstanceOf(EmployeeService.ValidationException.class);
    }

    @Test
    @DisplayName("TERMINATED is terminal — a leaver is not reinstated on the same row")
    void terminationIsFinal() {
        UUID id = service.create(request("E-1")).id();
        service.update(id, withStatus(EmploymentStatus.TERMINATED, JOINED.plusYears(1)));

        assertThatThrownBy(() -> service.update(id, withStatus(EmploymentStatus.ACTIVE, null)))
                .isInstanceOf(EmployeeService.ValidationException.class)
                .hasMessageContaining("TERMINATED");
    }

    @Test
    @DisplayName("An update that omits the status leaves it alone, rather than reviving a leaver")
    void omittedStatusIsNotAChange() {
        UUID id = service.create(request("E-1")).id();
        service.update(id, withStatus(EmploymentStatus.SUSPENDED, null));

        EmployeeResponse updated = service.update(
                id, new EmployeeRequest("E-1", "Asha", null, "Rao", null, JOINED, null, null, null, null, null));

        assertThat(updated.status()).isEqualTo(EmploymentStatus.SUSPENDED);
    }

    @Test
    @DisplayName("The portal flag can be turned off, and stays off")
    void portalFlagIsSettable() {
        UUID id = service.create(request("E-1")).id();

        EmployeeResponse updated = service.update(
                id, new EmployeeRequest("E-1", "Asha", null, "Rao", null, JOINED, null, null, null, null, false));

        assertThat(updated.portalEnabled()).isFalse();
    }

    private static EmployeeRequest withStatus(EmploymentStatus status, LocalDate terminationDate) {
        return new EmployeeRequest("E-1", "Asha", null, "Rao", null, JOINED, terminationDate, status, null, null, null);
    }
}
