package com.infinevo.core.employee.detail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.Employee;
import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.core.employee.EmployeeService;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.tenant.TenantContext;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * W-13.2 — first write creates, second updates, there is never a second row per section, and the
 * hand-written validation rules (spec section 7).
 *
 * <p>The repositories are small in-memory stand-ins rather than sets of one-shot stubs. The point of
 * the create-then-update test is that the second {@code put} finds the row the first one wrote; a
 * stub told to return empty twice would prove only that it was told to. Here the store keeps the
 * row, the finder applies the same {@code tenantId} and {@code employeeId} predicates the generated
 * query does, and the test fails if {@link AbstractEmployeeDetailServiceImpl} ever reads through a
 * path that does not.
 *
 * <p>No Spring context, as {@code docs/CONVENTIONS.md} section 3 requires of a {@code *Test}.
 * Row-level security is not in scope here and cannot be — that is {@link EmployeeDetailRlsIT},
 * against real Postgres.
 */
class EmployeeDetailServiceTest {

    private static final UUID TENANT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TENANT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID EMPLOYEE_A = UUID.fromString("aaaaaaaa-0000-4000-8000-00000000000a");
    private static final UUID EMPLOYEE_B = UUID.fromString("bbbbbbbb-0000-4000-8000-00000000000b");

    private EmployeeRepository employees;

    private EmployeePersonalServiceImpl personalService;
    private EmployeeContactServiceImpl contactService;
    private EmployeeIdentificationServiceImpl identificationService;
    private EmployeeEmploymentServiceImpl employmentService;
    private EmployeeBankServiceImpl bankService;

    private List<EmployeePersonal> personalStore;
    private List<EmployeeBank> bankStore;

    @BeforeEach
    void setUp() {
        employees = mock(EmployeeRepository.class);
        // Only tenant A's employee is live. Tenant B's belongs to another tenant, and there is no
        // third id at all - so "wrong tenant" and "no such employee" both fall out of this one stub,
        // which is exactly how the real tenant-scoped finder behaves.
        when(employees.findByIdAndTenantIdAndDeletedFalse(any(UUID.class), any(UUID.class)))
                .thenAnswer(inv -> {
                    UUID id = inv.getArgument(0);
                    UUID tenantId = inv.getArgument(1);
                    if (EMPLOYEE_A.equals(id) && TENANT_A.equals(tenantId)) {
                        return Optional.of(employee(EMPLOYEE_A));
                    }
                    if (EMPLOYEE_B.equals(id) && TENANT_B.equals(tenantId)) {
                        return Optional.of(employee(EMPLOYEE_B));
                    }
                    return Optional.empty();
                });

        personalStore = new ArrayList<>();
        bankStore = new ArrayList<>();

        PermissionService permissionService = mock(PermissionService.class);
        when(permissionService.holds("core.employee.update")).thenReturn(true);
        EmployeeService employeeService = mock(EmployeeService.class);

        personalService = new EmployeePersonalServiceImpl(
                store(EmployeePersonalRepository.class, personalStore), employees, permissionService, employeeService);
        contactService = new EmployeeContactServiceImpl(
                store(EmployeeContactRepository.class, new ArrayList<>()),
                employees,
                permissionService,
                employeeService);
        identificationService = new EmployeeIdentificationServiceImpl(
                store(EmployeeIdentificationRepository.class, new ArrayList<>()), employees);
        employmentService = new EmployeeEmploymentServiceImpl(
                store(EmployeeEmploymentRepository.class, new ArrayList<>()), employees);
        bankService = new EmployeeBankServiceImpl(store(EmployeeBankRepository.class, bankStore), employees);

        TenantContext.set(TENANT_A);
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    /**
     * A stand-in for one section's table.
     *
     * <p>{@code saveAndFlush} appends only when the instance is not already held, which is the
     * database's behaviour and the whole subject of the first two tests: an update saves the same
     * object, so the list must not grow.
     */
    private <E extends EmployeeDetail, R extends EmployeeDetailRepository<E>> R store(Class<R> type, List<E> rows) {
        R repository = mock(type);
        when(repository.saveAndFlush(any())).thenAnswer(inv -> {
            E entity = inv.getArgument(0);
            if (entity.getId() == null) {
                // Stands in for the database default on id; Hibernate assigns it in the real thing.
                ReflectionTestUtils.setField(entity, "id", UUID.randomUUID(), UUID.class);
            }
            if (!rows.contains(entity)) {
                rows.add(entity);
            }
            return entity;
        });
        when(repository.findByTenantIdAndEmployeeId(any(UUID.class), any(UUID.class)))
                .thenAnswer(inv -> {
                    UUID tenantId = inv.getArgument(0);
                    UUID employeeId = inv.getArgument(1);
                    return rows.stream()
                            .filter(row -> tenantId.equals(row.getTenantId()))
                            .filter(row -> employeeId.equals(row.getEmployee().getId()))
                            .findFirst();
                });
        return repository;
    }

    /** A persisted employee, as the tenant-scoped finder would return one. */
    private static Employee employee(UUID id) {
        Employee employee = mock(Employee.class);
        when(employee.getId()).thenReturn(id);
        return employee;
    }

    private static EmployeePersonalRequest personal(String nationality) {
        return new EmployeePersonalRequest(
                LocalDate.of(1990, 5, 17), "SINGLE", nationality, null, "Ramesh Rao", null, null);
    }

    private static EmployeeBankRequest bank(String accountNumber) {
        return new EmployeeBankRequest(
                PaymentMode.BANK_TRANSFER,
                "Asha Rao",
                "State Bank",
                "SBIN0001234",
                accountNumber,
                BankAccountType.SAVINGS);
    }

    @Nested
    @DisplayName("put creates on the first write and replaces on every later one")
    class CreateThenReplace {

        @Test
        @DisplayName("The first put creates the row")
        void firstWriteCreates() {
            assertThat(personalStore).isEmpty();

            EmployeePersonalResponse created = personalService.put(EMPLOYEE_A, personal("Indian"));

            assertThat(personalStore).hasSize(1);
            assertThat(created.employeeId()).isEqualTo(EMPLOYEE_A);
            assertThat(created.tenantId()).isEqualTo(TENANT_A);
            assertThat(created.nationality()).isEqualTo("Indian");
            assertThat(created.dateOfBirth()).isEqualTo(LocalDate.of(1990, 5, 17));
        }

        @Test
        @DisplayName("The second put updates that row — there is never a second row per section")
        void secondWriteUpdatesAndDoesNotAddARow() {
            EmployeePersonalResponse created = personalService.put(EMPLOYEE_A, personal("Indian"));
            EmployeePersonalResponse replaced = personalService.put(EMPLOYEE_A, personal("British"));

            assertThat(personalStore)
                    .as("a second PUT must not write a second personal section")
                    .hasSize(1);
            assertThat(replaced.id()).isEqualTo(created.id());
            assertThat(replaced.nationality()).isEqualTo("British");
            assertThat(personalService.get(EMPLOYEE_A).nationality()).isEqualTo("British");
        }

        @Test
        @DisplayName("A field omitted on the second put is cleared — PUT replaces, it does not patch")
        void omittedFieldIsCleared() {
            personalService.put(EMPLOYEE_A, personal("Indian"));
            personalService.put(EMPLOYEE_A, new EmployeePersonalRequest(null, null, null, null, null, null, null));

            assertThat(personalStore).hasSize(1);
            assertThat(personalService.get(EMPLOYEE_A).nationality()).isNull();
            assertThat(personalService.get(EMPLOYEE_A).fatherName()).isNull();
        }

        @Test
        @DisplayName("All five sections create independently for the same employee")
        void allFiveSectionsAreIndependent() {
            personalService.put(EMPLOYEE_A, personal("Indian"));
            contactService.put(EMPLOYEE_A, contactRequest("asha@example.com"));
            identificationService.put(EMPLOYEE_A, identificationRequest("ABCDE1234F", "123456789012"));
            employmentService.put(EMPLOYEE_A, employmentRequest(LocalTime.of(9, 0), LocalTime.of(18, 0)));
            bankService.put(EMPLOYEE_A, bank("0012345678"));

            assertThat(personalService.get(EMPLOYEE_A).nationality()).isEqualTo("Indian");
            assertThat(contactService.get(EMPLOYEE_A).personalEmail()).isEqualTo("asha@example.com");
            assertThat(identificationService.get(EMPLOYEE_A).panNumber()).isEqualTo("ABCDE1234F");
            assertThat(employmentService.get(EMPLOYEE_A).shiftEndTime()).isEqualTo(LocalTime.of(18, 0));
            // The leading zeros survive, which they could not if the column or the field were numeric.
            assertThat(bankService.get(EMPLOYEE_A).bankAccountNumber()).isEqualTo("0012345678");
        }
    }

    @Nested
    @DisplayName("the employee must exist, be in the bound tenant and not be soft-deleted")
    class EmployeeMustBeVisible {

        @Test
        @DisplayName("A section for an employee in another tenant is a 404, on read and on write")
        void anotherTenantsEmployeeIsNotFound() {
            assertThatThrownBy(() -> personalService.get(EMPLOYEE_B))
                    .isInstanceOf(EmployeeDetailService.NotFoundException.class);
            assertThatThrownBy(() -> personalService.put(EMPLOYEE_B, personal("Indian")))
                    .isInstanceOf(EmployeeDetailService.NotFoundException.class);
            assertThat(personalStore)
                    .as("a refused put must write nothing at all")
                    .isEmpty();
        }

        @Test
        @DisplayName("An employee that does not exist is a 404")
        void unknownEmployeeIsNotFound() {
            assertThatThrownBy(() -> bankService.put(UUID.randomUUID(), bank("0012345678")))
                    .isInstanceOf(EmployeeDetailService.NotFoundException.class);
        }

        @Test
        @DisplayName("A section that has never been written is a 404, and the employee still exists")
        void unwrittenSectionIsNotFound() {
            assertThatThrownBy(() -> bankService.get(EMPLOYEE_A))
                    .isInstanceOf(EmployeeDetailService.NotFoundException.class)
                    .hasMessageContaining("bank section");
        }

        @Test
        @DisplayName("A soft-deleted employee has no sections, on read or on write")
        void softDeletedEmployeeIsNotFound() {
            // W-13.2 review, F-5. The other cases here lean on the finder's tenant argument, so
            // they would still pass if the service called a finder that ignored is_deleted. This
            // one cannot: the id and the tenant both match, and only the soft-delete flag makes it
            // invisible. A section is reachable only through the employee, so terminating someone
            // must take their bank and identity details out of reach with them.
            UUID deleted = UUID.fromString("dddddddd-0000-4000-8000-00000000000d");
            when(employees.findByIdAndTenantIdAndDeletedFalse(deleted, TENANT_A))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> bankService.get(deleted))
                    .isInstanceOf(EmployeeDetailService.NotFoundException.class);
            assertThatThrownBy(() -> bankService.put(deleted, bank("0012345678")))
                    .isInstanceOf(EmployeeDetailService.NotFoundException.class);
            assertThat(bankStore)
                    .as("a section must not be written for an employee nobody can read")
                    .isEmpty();
        }

        @Test
        @DisplayName("With no tenant bound nothing is readable or writable")
        void unboundTenantIsRefused() {
            TenantContext.clear();

            assertThatThrownBy(() -> personalService.get(EMPLOYEE_A)).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> personalService.put(EMPLOYEE_A, personal("Indian")))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("validation, hand-written so it runs with no Spring context")
    class Validation {

        @Test
        @DisplayName("A null body is a field error, not a NullPointerException")
        void nullBodyIsRejected() {
            assertThatThrownBy(() -> personalService.put(EMPLOYEE_A, null))
                    .isInstanceOf(EmployeeDetailService.ValidationException.class);
        }

        @Test
        @DisplayName("A value longer than its column is refused, naming the field and the width")
        void tooLongIsRefusedPerColumnWidth() {
            assertThatThrownBy(() -> personalService.put(
                            EMPLOYEE_A,
                            new EmployeePersonalRequest(null, "x".repeat(33), null, null, null, null, null)))
                    .isInstanceOf(EmployeeDetailService.ValidationException.class)
                    .satisfies(e -> assertThat(((EmployeeDetailService.ValidationException) e).fieldErrors())
                            .containsEntry("maritalStatus", "maritalStatus must be at most 32 characters"));

            // The boundary itself is fine - 32 is the column width, not one over it.
            assertThat(personalService
                            .put(
                                    EMPLOYEE_A,
                                    new EmployeePersonalRequest(null, "x".repeat(32), null, null, null, null, null))
                            .maritalStatus())
                    .hasSize(32);
        }

        @Test
        @DisplayName("PAN must be AAAAA9999A in upper case — the frozen pattern, Identification.java:23")
        void panShapeIsChecked() {
            assertThat(fieldErrors(
                            () -> identificationService.put(EMPLOYEE_A, identificationRequest("abcde1234f", null))))
                    .containsKey("panNumber");
            assertThat(fieldErrors(
                            () -> identificationService.put(EMPLOYEE_A, identificationRequest("ABCD1234F", null))))
                    .containsKey("panNumber");
            assertThat(identificationService
                            .put(EMPLOYEE_A, identificationRequest("ABCDE1234F", null))
                            .panNumber())
                    .isEqualTo("ABCDE1234F");
        }

        @Test
        @DisplayName("Aadhaar must be twelve digits — the frozen pattern, Identification.java:19")
        void aadhaarShapeIsChecked() {
            assertThat(fieldErrors(
                            () -> identificationService.put(EMPLOYEE_A, identificationRequest(null, "12345678901"))))
                    .containsKey("aadhaarNumber");
            assertThat(fieldErrors(
                            () -> identificationService.put(EMPLOYEE_A, identificationRequest(null, "12345678901A"))))
                    .containsKey("aadhaarNumber");
            assertThat(identificationService
                            .put(EMPLOYEE_A, identificationRequest(null, "123456789012"))
                            .aadhaarNumber())
                    .isEqualTo("123456789012");
        }

        @Test
        @DisplayName("personalEmail must look like an email address")
        void emailShapeIsChecked() {
            assertThat(fieldErrors(() -> contactService.put(EMPLOYEE_A, contactRequest("not-an-address"))))
                    .containsKey("personalEmail");
            assertThat(contactService
                            .put(EMPLOYEE_A, contactRequest("asha.rao@example.co.in"))
                            .personalEmail())
                    .isEqualTo("asha.rao@example.co.in");
        }

        @Test
        @DisplayName("shiftEndTime must be after shiftStartTime when both are given")
        void shiftOrderIsChecked() {
            assertThat(fieldErrors(() -> employmentService.put(
                            EMPLOYEE_A, employmentRequest(LocalTime.of(18, 0), LocalTime.of(9, 0)))))
                    .containsKey("shiftEndTime");
            assertThat(fieldErrors(() -> employmentService.put(
                            EMPLOYEE_A, employmentRequest(LocalTime.of(9, 0), LocalTime.of(9, 0)))))
                    .as("a shift with no duration is a typo, not a night shift")
                    .containsKey("shiftEndTime");

            // One time on its own says nothing about order and must not be refused.
            assertThat(employmentService
                            .put(EMPLOYEE_A, employmentRequest(LocalTime.of(9, 0), null))
                            .shiftStartTime())
                    .isEqualTo(LocalTime.of(9, 0));
        }

        @Test
        @DisplayName("paymentMode is required — the column is NOT NULL")
        void paymentModeIsRequired() {
            assertThat(fieldErrors(() -> bankService.put(
                            EMPLOYEE_A,
                            new EmployeeBankRequest(null, "Asha Rao", "State Bank", "SBIN0001234", "12345", null))))
                    .containsEntry("paymentMode", "paymentMode is required");
        }

        @Test
        @DisplayName("A blank string is stored as null, not as an empty string")
        void blankBecomesNull() {
            assertThat(personalService
                            .put(EMPLOYEE_A, new EmployeePersonalRequest(null, "   ", null, null, null, null, null))
                            .maritalStatus())
                    .isNull();
        }
    }

    /** Runs something that should fail validation and hands back what it complained about. */
    private static java.util.Map<String, String> fieldErrors(Runnable call) {
        try {
            call.run();
        } catch (EmployeeDetailService.ValidationException e) {
            return e.fieldErrors();
        }
        throw new AssertionError("expected a ValidationException and got none");
    }

    private static EmployeeContactRequest contactRequest(String personalEmail) {
        return new EmployeeContactRequest(
                personalEmail,
                "9876543210",
                "12 MG Road",
                null,
                "Pune",
                "Maharashtra",
                "27",
                "411001",
                "7 Old Street",
                null,
                "Nagpur",
                "Maharashtra",
                "27",
                "440001",
                "Ramesh Rao",
                "9876500000",
                "Father",
                null,
                null,
                null,
                null,
                null);
    }

    private static EmployeeIdentificationRequest identificationRequest(String pan, String aadhaar) {
        return new EmployeeIdentificationRequest(
                "CITIZEN", aadhaar, pan, null, null, "PASSPORT", "Passport", "Z1234567", null, null, null);
    }

    private static EmployeeEmploymentRequest employmentRequest(LocalTime start, LocalTime end) {
        return new EmployeeEmploymentRequest("G5", "WS-14", "Asia/Kolkata", start, end, "Joined the Pune office");
    }
}
