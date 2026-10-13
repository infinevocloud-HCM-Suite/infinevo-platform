package com.infinevo.core.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.infinevo.core.approval.ApprovalInstanceRepository;
import com.infinevo.core.approval.ApprovalService;
import com.infinevo.core.document.DocumentRepository;
import com.infinevo.core.employee.EmployeeRepository;
import com.infinevo.core.employee.EmployeeService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * D-80: a leave request reply carries the employee's and the leave type's names, read in one batch per
 * page - one {@code displayNames} call and one leave type read - so no screen loads every employee.
 */
class LeaveRequestNamesTest {

    private final UUID tenantId = UUID.randomUUID();
    private final UUID asha = UUID.randomUUID();
    private final UUID ravi = UUID.randomUUID();
    private final UUID casualId = UUID.randomUUID();
    private final UUID sickId = UUID.randomUUID();

    private LeaveRequestRepository requests;
    private LeaveTypeRepository types;
    private EmployeeService employeeService;
    private LeaveRequestServiceImpl service;

    @BeforeEach
    void setUp() {
        requests = mock(LeaveRequestRepository.class);
        types = mock(LeaveTypeRepository.class);
        employeeService = mock(EmployeeService.class);
        service = new LeaveRequestServiceImpl(
                requests,
                mock(LeaveRequestDocumentRepository.class),
                types,
                mock(LeavePolicyRepository.class),
                mock(LeaveEligibilityService.class),
                mock(LeaveBalanceService.class),
                mock(WorkingDayCalculator.class),
                mock(ApprovalService.class),
                mock(ApprovalInstanceRepository.class),
                mock(EmployeeRepository.class),
                mock(LeaveConsumptionService.class),
                mock(DocumentRepository.class),
                employeeService);

        LeaveType casual = new LeaveType(
                tenantId, "CL", "Casual Leave", true, LeaveUnit.DAYS, true, LocalDate.of(2026, 1, 1), null, true);
        casual.setId(casualId);
        LeaveType sick = new LeaveType(
                tenantId, "SL", "Sick Leave", true, LeaveUnit.DAYS, true, LocalDate.of(2026, 1, 1), null, true);
        sick.setId(sickId);
        when(types.findByTenantIdAndIdIn(eq(tenantId), anyCollection())).thenReturn(List.of(casual, sick));
        when(employeeService.displayNames(anyCollection())).thenReturn(Map.of(asha, "Asha Rao", ravi, "Ravi Kumar"));
    }

    @Test
    @DisplayName("a page of requests names each employee and leave type, with one read of each")
    void pageCarriesNames() {
        List<LeaveRequest> rows = List.of(request(asha, casualId), request(ravi, sickId), request(asha, sickId));
        Pageable pageable = PageRequest.of(0, 25);
        when(requests.search(tenantId, null, null, null, null, pageable)).thenReturn(new PageImpl<>(rows, pageable, 3));

        Page<LeaveRequestResponse> page = service.searchRequests(tenantId, null, null, null, null, pageable);

        assertThat(page.getContent())
                .extracting(LeaveRequestResponse::employeeName)
                .containsExactly("Asha Rao", "Ravi Kumar", "Asha Rao");
        assertThat(page.getContent())
                .extracting(LeaveRequestResponse::leaveTypeName)
                .containsExactly("Casual Leave", "Sick Leave", "Sick Leave");
        assertThat(page.getContent())
                .extracting(LeaveRequestResponse::employeeId)
                .containsExactly(asha, ravi, asha);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UUID>> employeeIds = ArgumentCaptor.forClass(Collection.class);
        verify(employeeService, times(1)).displayNames(employeeIds.capture());
        assertThat(employeeIds.getValue()).containsExactlyInAnyOrder(asha, ravi);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UUID>> typeIds = ArgumentCaptor.forClass(Collection.class);
        verify(types, times(1)).findByTenantIdAndIdIn(eq(tenantId), typeIds.capture());
        assertThat(typeIds.getValue()).containsExactlyInAnyOrder(casualId, sickId);
    }

    @Test
    @DisplayName("the team view's page is named the same way")
    void teamPageCarriesNames() {
        Pageable pageable = PageRequest.of(0, 25);
        when(requests.searchForEmployees(eq(tenantId), anyCollection(), any(), any(), any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(request(ravi, casualId)), pageable, 1));

        Page<LeaveRequestResponse> page =
                service.searchRequestsForEmployees(tenantId, List.of(ravi), null, null, null, pageable);

        assertThat(page.getContent().get(0).employeeName()).isEqualTo("Ravi Kumar");
        assertThat(page.getContent().get(0).leaveTypeName()).isEqualTo("Casual Leave");
    }

    @Test
    @DisplayName("one request read by id carries both names")
    void singleRequestCarriesNames() {
        LeaveRequest row = request(asha, casualId);
        when(requests.findByIdAndTenantId(row.getId(), tenantId)).thenReturn(Optional.of(row));

        LeaveRequestResponse response = service.getRequest(tenantId, row.getId());

        assertThat(response.employeeName()).isEqualTo("Asha Rao");
        assertThat(response.leaveTypeName()).isEqualTo("Casual Leave");
    }

    @Test
    @DisplayName("an empty page reads no names at all")
    void emptyPageReadsNothing() {
        Pageable pageable = PageRequest.of(0, 25);
        when(requests.search(tenantId, null, null, null, null, pageable)).thenReturn(Page.empty(pageable));

        service.searchRequests(tenantId, null, null, null, null, pageable);

        verify(employeeService, times(0)).displayNames(anyCollection());
        verify(types, times(0)).findByTenantIdAndIdIn(any(), anyCollection());
    }

    private LeaveRequest request(UUID employeeId, UUID leaveTypeId) {
        LeaveRequest row = new LeaveRequest(
                tenantId,
                employeeId,
                leaveTypeId,
                LocalDate.of(2026, 10, 20),
                LocalDate.of(2026, 10, 21),
                false,
                null,
                new BigDecimal("2.00"),
                "Family",
                LeaveRequestStatus.PENDING,
                null,
                false);
        row.setId(UUID.randomUUID());
        return row;
    }
}
