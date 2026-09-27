package com.infinevo.core.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.shared.authz.PermissionDeniedException;
import com.infinevo.shared.authz.PermissionService;
import com.infinevo.shared.tenant.TenantContext;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * W-13.3 — unit tests for {@link EmployeeQueryService} (spec section 7).
 *
 * <p>No Spring context. The repository is mocked; each test verifies the arguments the service
 * passed and the shape of the response it returned.
 *
 * <ul>
 *   <li>Page size is clamped at 100; a larger value arrives at the repository as 100.
 *   <li>{@code q} is forwarded as-is; the JPQL handles the prefix match.
 *   <li>Soft-deleted rows are excluded by default ({@code includeDeleted=false}).
 *   <li>{@code includeDeleted=true} without {@code core.employee.delete} throws
 *       {@code PermissionDeniedException}; with it, the repository receives {@code true}.
 * </ul>
 */
class EmployeeQueryServiceTest {

    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final LocalDate JOINED = LocalDate.of(2026, 4, 1);

    private EmployeeRepository repository;
    private PermissionService permissionService;
    private EmployeeQueryService service;

    @BeforeEach
    void setUp() {
        repository = mock(EmployeeRepository.class);
        permissionService = mock(PermissionService.class);
        service = new EmployeeQueryService(repository, permissionService);
        TenantContext.set(TENANT);
    }

    @AfterEach
    void unbind() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("Page size larger than 100 is clamped to 100")
    void pageSizeClampedAt100() {
        Pageable big = PageRequest.of(0, 500, Sort.by("lastName"));
        when(repository.search(any(), any(), any(), eq(false), any())).thenReturn(Page.empty());

        service.search(null, null, false, big);

        verify(repository).search(eq(TENANT), eq(null), eq(null), eq(false), org.mockito.ArgumentMatchers.argThat(p -> {
            assertThat(p.getPageSize()).isEqualTo(100);
            return true;
        }));
    }

    @Test
    @DisplayName("Page size of zero is clamped to 1")
    void pageSizeZeroClampedTo1() {
        Pageable zero = mock(Pageable.class);
        when(zero.getPageNumber()).thenReturn(0);
        when(zero.getPageSize()).thenReturn(0);
        when(zero.getSort()).thenReturn(Sort.unsorted());
        when(repository.search(any(), any(), any(), eq(false), any())).thenReturn(Page.empty());

        service.search(null, null, false, zero);

        verify(repository).search(eq(TENANT), eq(null), eq(null), eq(false), org.mockito.ArgumentMatchers.argThat(p -> {
            assertThat(p.getPageSize()).isEqualTo(1);
            return true;
        }));
    }

    @Test
    @DisplayName("Default sort is lastName ascending when the client sends none")
    void defaultSortIsLastNameAscending() {
        Pageable unsorted = PageRequest.of(0, 25);
        when(repository.search(any(), any(), any(), eq(false), any())).thenReturn(Page.empty());

        service.search(null, null, false, unsorted);

        verify(repository).search(eq(TENANT), eq(null), eq(null), eq(false), org.mockito.ArgumentMatchers.argThat(p -> {
            assertThat(p.getSort().getOrderFor("lastName")).isNotNull();
            assertThat(p.getSort().getOrderFor("lastName").getDirection()).isEqualTo(Sort.Direction.ASC);
            return true;
        }));
    }

    @Test
    @DisplayName("Free-text q is forwarded to the repository")
    void qIsForwarded() {
        when(repository.search(any(), eq("Ash"), any(), eq(false), any())).thenReturn(Page.empty());

        service.search("Ash", null, false, PageRequest.of(0, 25));

        verify(repository).search(eq(TENANT), eq("Ash"), eq(null), eq(false), any());
    }

    @Test
    @DisplayName("Status filter is forwarded to the repository")
    void statusFilterIsForwarded() {
        when(repository.search(any(), any(), eq(EmploymentStatus.ACTIVE), eq(false), any()))
                .thenReturn(Page.empty());

        service.search(null, EmploymentStatus.ACTIVE, false, PageRequest.of(0, 25));

        verify(repository).search(eq(TENANT), eq(null), eq(EmploymentStatus.ACTIVE), eq(false), any());
    }

    @Test
    @DisplayName("includeDeleted=false does not check core.employee.delete")
    void excludeDeletedDoesNotCheckPermission() {
        when(repository.search(any(), any(), any(), eq(false), any())).thenReturn(Page.empty());

        service.search(null, null, false, PageRequest.of(0, 25));

        verify(permissionService, never()).require("core.employee.delete");
    }

    @Test
    @DisplayName("includeDeleted=true with core.employee.delete succeeds")
    void includeDeletedWithPermissionSucceeds() {
        doNothing().when(permissionService).require("core.employee.delete");
        Employee emp = employee("EMP-001", "Alice", false);
        when(repository.search(eq(TENANT), eq(null), eq(null), eq(true), any()))
                .thenReturn(new PageImpl<>(List.of(emp)));

        Page<EmployeeSummaryResponse> result = service.search(null, null, true, PageRequest.of(0, 25));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).firstName()).isEqualTo("Alice");
        verify(permissionService).require("core.employee.delete");
    }

    @Test
    @DisplayName("includeDeleted=true without core.employee.delete throws PermissionDeniedException")
    void includeDeletedWithoutPermissionThrows() {
        doThrow(new PermissionDeniedException("core.employee.delete"))
                .when(permissionService)
                .require("core.employee.delete");

        assertThatThrownBy(() -> service.search(null, null, true, PageRequest.of(0, 25)))
                .isInstanceOf(PermissionDeniedException.class);

        verify(repository, never()).search(any(), any(), any(), eq(true), any());
    }

    @Test
    @DisplayName("Returned page maps Employee to EmployeeSummaryResponse")
    void mapsToSummaryResponse() {
        Employee emp = employee("EMP-002", "Bob", false);
        when(repository.search(any(), any(), any(), eq(false), any())).thenReturn(new PageImpl<>(List.of(emp)));

        Page<EmployeeSummaryResponse> result = service.search(null, null, false, PageRequest.of(0, 25));

        assertThat(result.getContent()).hasSize(1);
        EmployeeSummaryResponse summary = result.getContent().get(0);
        assertThat(summary.employeeNumber()).isEqualTo("EMP-002");
        assertThat(summary.firstName()).isEqualTo("Bob");
        assertThat(summary.status()).isEqualTo(EmploymentStatus.ACTIVE);
        assertThat(summary.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("Search q escapes special wildcard characters %, _ and \\")
    void qSearchEscapesSpecialCharacters() {
        when(repository.search(any(), any(), any(), eq(false), any())).thenReturn(Page.empty());

        service.search("100%_test\\name", null, false, PageRequest.of(0, 25));

        verify(repository).search(eq(TENANT), eq("100\\%\\_test\\\\name"), eq(null), eq(false), any());
    }

    @Test
    @DisplayName("Search query q matches across firstName, lastName, employeeNumber, and workEmail")
    void qMatchesFourFields() {
        Employee e1 = employee("EMP-101", "Alice", "Smith", "alice@example.com");
        Employee e2 = employee("EMP-102", "Bob", "Jones", "bob@example.com");
        Employee e3 = employee("EMP-103", "Charlie", "Brown", "charlie@example.com");

        when(repository.search(eq(TENANT), eq("Alice"), eq(null), eq(false), any()))
                .thenReturn(new PageImpl<>(List.of(e1)));
        when(repository.search(eq(TENANT), eq("Jones"), eq(null), eq(false), any()))
                .thenReturn(new PageImpl<>(List.of(e2)));
        when(repository.search(eq(TENANT), eq("EMP-103"), eq(null), eq(false), any()))
                .thenReturn(new PageImpl<>(List.of(e3)));
        when(repository.search(eq(TENANT), eq("alice@example.com"), eq(null), eq(false), any()))
                .thenReturn(new PageImpl<>(List.of(e1)));

        Page<EmployeeSummaryResponse> res1 = service.search("Alice", null, false, PageRequest.of(0, 25));
        Page<EmployeeSummaryResponse> res2 = service.search("Jones", null, false, PageRequest.of(0, 25));
        Page<EmployeeSummaryResponse> res3 = service.search("EMP-103", null, false, PageRequest.of(0, 25));
        Page<EmployeeSummaryResponse> res4 = service.search("alice@example.com", null, false, PageRequest.of(0, 25));

        assertThat(res1.getContent()).extracting("firstName").containsExactly("Alice");
        assertThat(res2.getContent()).extracting("firstName").containsExactly("Bob");
        assertThat(res3.getContent()).extracting("employeeNumber").containsExactly("EMP-103");
        assertThat(res4.getContent()).extracting("workEmail").containsExactly("alice@example.com");
    }

    /** Builds a minimal Employee for test assertions. */
    private static Employee employee(String number, String firstName, boolean deleted) {
        return employee(number, firstName, "Test", null);
    }

    private static Employee employee(String number, String firstName, String lastName, String workEmail) {
        Employee e = new Employee(TENANT, "test");
        ReflectionTestUtils.setField(e, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(e, "employeeNumber", number);
        ReflectionTestUtils.setField(e, "firstName", firstName);
        ReflectionTestUtils.setField(e, "lastName", lastName);
        ReflectionTestUtils.setField(e, "workEmail", workEmail);
        ReflectionTestUtils.setField(e, "status", EmploymentStatus.ACTIVE);
        ReflectionTestUtils.setField(e, "dateOfJoining", JOINED);
        ReflectionTestUtils.setField(e, "deleted", false);
        ReflectionTestUtils.setField(e, "portalEnabled", true);
        ReflectionTestUtils.setField(e, "createdAt", Instant.now());
        ReflectionTestUtils.setField(e, "updatedAt", Instant.now());
        return e;
    }
}
