package com.infinevo.core.org;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.shared.tenant.TenantContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * W-14.1 — the three rules spec section 7 names for the org masters: a duplicate code is refused
 * within a tenant, an assigned record cannot be deleted, and an assigned record <em>can</em> be
 * deactivated.
 *
 * <p>The last two are one decision seen from both sides, and testing only the refusal would miss the
 * point of it. Spec section 4 keeps Payroll's status flag precisely so that a department nobody
 * should pick any more can be retired without touching the employees holding it. A build that
 * refused the delete and also refused the deactivation would satisfy half the spec and leave an
 * administrator with no way through at all.
 *
 * <p>The repositories are small in-memory stand-ins rather than one-shot stubs, for the reason
 * {@code EmployeeServiceImplTest} gives: a stub told to report a duplicate proves only that it was
 * told to, while a store that applies the same {@code (tenant_id, code)} predicate the generated
 * query does fails if the service ever checks uniqueness without the tenant.
 *
 * <p>No Spring context, as {@code docs/CONVENTIONS.md} section 3 requires of a {@code *Test}.
 * Row-level security is not in scope here and cannot be — that is {@link OrgMasterRlsIT}, against
 * real Postgres.
 */
class OrgMasterServiceTest {

    private static final UUID TENANT_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TENANT_B = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final Map<UUID, Department> departments = new LinkedHashMap<>();
    private final Map<UUID, Designation> designations = new LinkedHashMap<>();

    private EmployeeRepository employeeRepository;
    private DepartmentService departmentService;
    private DesignationService designationService;

    @BeforeEach
    void setUp() {
        departments.clear();
        designations.clear();
        employeeRepository = mock(EmployeeRepository.class);

        DepartmentRepository departmentRepository = mock(DepartmentRepository.class);
        wire(departmentRepository, departments);
        departmentService = new DepartmentServiceImpl(departmentRepository, employeeRepository);

        DesignationRepository designationRepository = mock(DesignationRepository.class);
        wire(designationRepository, designations);
        designationService = new DesignationServiceImpl(designationRepository, employeeRepository);

        TenantContext.set(TENANT_A);
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("A second department with the same code in the same tenant is refused")
    void duplicateCodeRefusedWithinATenant() {
        departmentService.create(new DepartmentRequest("FIN", "Finance", null));

        assertThatThrownBy(() -> departmentService.create(new DepartmentRequest("FIN", "Finance and Accounts", null)))
                .isInstanceOf(OrgMasterService.DuplicateCodeException.class)
                .hasMessageContaining("FIN")
                .hasMessageContaining("this tenant");

        assertThat(departments).hasSize(1);
    }

    @Test
    @DisplayName("The same code in another tenant is accepted — the index is (tenant_id, code)")
    void sameCodeInAnotherTenantIsAccepted() {
        departmentService.create(new DepartmentRequest("FIN", "Finance", null));

        TenantContext.set(TENANT_B);
        DepartmentResponse inB = departmentService.create(new DepartmentRequest("FIN", "Finanzas", null));

        assertThat(inB.tenantId()).isEqualTo(TENANT_B);
        assertThat(departments).hasSize(2);
    }

    @Test
    @DisplayName("An update cannot take a code another department in the same tenant already holds")
    void updateCannotTakeAnotherCode() {
        departmentService.create(new DepartmentRequest("FIN", "Finance", null));
        UUID second = departmentService
                .create(new DepartmentRequest("OPS", "Operations", null))
                .id();

        assertThatThrownBy(() -> departmentService.update(second, new DepartmentRequest("FIN", "Operations", null)))
                .isInstanceOf(OrgMasterService.DuplicateCodeException.class);
    }

    @Test
    @DisplayName("A designation follows the same rule — the two masters are one service over two tables")
    void duplicateDesignationCodeRefused() {
        designationService.create(new DesignationRequest("MGR", "Manager", null));

        assertThatThrownBy(() -> designationService.create(new DesignationRequest("MGR", "Manager II", null)))
                .isInstanceOf(OrgMasterService.DuplicateCodeException.class);
    }

    @Test
    @DisplayName("Deleting a department an employee is assigned to is refused, and the row stays")
    void deletingAnAssignedRecordIsRefused() {
        UUID id = departmentService
                .create(new DepartmentRequest("FIN", "Finance", null))
                .id();
        when(employeeRepository.countByTenantIdAndDepartment_Id(TENANT_A, id)).thenReturn(3L);

        assertThatThrownBy(() -> departmentService.delete(id))
                .isInstanceOf(OrgMasterService.RecordInUseException.class)
                .hasMessageContaining("3 employees")
                .hasMessageContaining("Deactivate");

        assertThat(departments).containsKey(id);
    }

    @Test
    @DisplayName("Deleting a department nobody is assigned to goes through")
    void deletingAnUnassignedRecordSucceeds() {
        UUID id = departmentService
                .create(new DepartmentRequest("FIN", "Finance", null))
                .id();

        departmentService.delete(id);

        assertThat(departments).doesNotContainKey(id);
    }

    @Test
    @DisplayName("Deactivating a department employees are assigned to is allowed — the supported alternative")
    void deactivatingAnAssignedRecordIsAllowed() {
        UUID id = departmentService
                .create(new DepartmentRequest("FIN", "Finance", null))
                .id();
        when(employeeRepository.countByTenantIdAndDepartment_Id(TENANT_A, id)).thenReturn(3L);

        DepartmentResponse deactivated = departmentService.update(id, new DepartmentRequest("FIN", "Finance", false));

        assertThat(deactivated.active()).isFalse();
        assertThat(departments.get(id).isActive()).isFalse();

        // ... and it then disappears from the list a new assignment is picked from, while still
        // being there for the employees who hold it.
        assertThat(departmentService.list(true)).isEmpty();
        assertThat(departmentService.list(false)).hasSize(1);
    }

    @Test
    @DisplayName("Omitting active on an update leaves it where it is, so nothing is silently reactivated")
    void omittingActiveOnUpdateKeepsIt() {
        UUID id = departmentService
                .create(new DepartmentRequest("FIN", "Finance", false))
                .id();

        DepartmentResponse renamed = departmentService.update(id, new DepartmentRequest("FIN", "Finance Dept", null));

        assertThat(renamed.active()).isFalse();
    }

    @Test
    @DisplayName("A blank code or name is a field error, not a constraint violation later")
    void blankFieldsAreRefused() {
        OrgMasterService.ValidationException thrown = catchThrowableOfType(
                () -> departmentService.create(new DepartmentRequest("   ", null, null)),
                OrgMasterService.ValidationException.class);

        assertThat(thrown.fieldErrors()).containsOnlyKeys("code", "name");
        assertThat(departments).isEmpty();
    }

    @Test
    @DisplayName("A department in another tenant is not found, rather than found and refused")
    void anotherTenantsRecordIsNotFound() {
        UUID id = departmentService
                .create(new DepartmentRequest("FIN", "Finance", null))
                .id();

        TenantContext.set(TENANT_B);
        assertThatThrownBy(() -> departmentService.delete(id)).isInstanceOf(OrgMasterService.NotFoundException.class);
    }

    /**
     * Makes one mocked repository behave like the table behind it.
     *
     * <p>Every finder applies the tenant predicate the generated query applies, so a service that
     * forgot the tenant would return another tenant's row here and fail the test rather than pass it.
     */
    private <T extends OrgMaster> void wire(OrgMasterRepository<T> repository, Map<UUID, T> store) {
        when(repository.saveAndFlush(any())).thenAnswer(inv -> {
            T entity = inv.getArgument(0);
            if (entity.getId() == null) {
                ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
            }
            store.put(entity.getId(), entity);
            return entity;
        });
        when(repository.findByIdAndTenantId(any(UUID.class), any(UUID.class)))
                .thenAnswer(inv -> Optional.ofNullable(store.get(inv.<UUID>getArgument(0)))
                        .filter(e -> e.getTenantId().equals(inv.getArgument(1))));
        when(repository.existsByTenantIdAndCode(any(UUID.class), anyString())).thenAnswer(inv -> store.values().stream()
                .anyMatch(e -> e.getTenantId().equals(inv.getArgument(0))
                        && e.getCode().equals(inv.getArgument(1))));
        when(repository.existsByTenantIdAndCodeAndIdNot(any(UUID.class), anyString(), any(UUID.class)))
                .thenAnswer(inv -> store.values().stream()
                        .anyMatch(e -> e.getTenantId().equals(inv.getArgument(0))
                                && e.getCode().equals(inv.getArgument(1))
                                && !e.getId().equals(inv.getArgument(2))));
        when(repository.findByTenantIdOrderByCodeAsc(any(UUID.class)))
                .thenAnswer(inv -> byTenant(store, inv.getArgument(0)));
        when(repository.findByTenantIdAndActiveTrueOrderByCodeAsc(any(UUID.class)))
                .thenAnswer(inv -> byTenant(store, inv.getArgument(0)).stream()
                        .filter(OrgMaster::isActive)
                        .toList());
        org.mockito.Mockito.doAnswer(inv -> store.remove(inv.<T>getArgument(0).getId()))
                .when(repository)
                .delete(any());
    }

    private static <T extends OrgMaster> List<T> byTenant(Map<UUID, T> store, UUID tenantId) {
        return store.values().stream()
                .filter(e -> e.getTenantId().equals(tenantId))
                .sorted(java.util.Comparator.comparing(OrgMaster::getCode))
                .toList();
    }
}
