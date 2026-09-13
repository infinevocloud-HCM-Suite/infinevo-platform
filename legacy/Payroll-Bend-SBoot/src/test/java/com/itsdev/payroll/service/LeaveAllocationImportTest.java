package com.itsdev.payroll.service;

import com.itsdev.payroll.dto.leave.LeaveAllocationImportResultDTO;
import com.itsdev.payroll.entity.employee.BasicDetails;
import com.itsdev.payroll.entity.leave.EmployeeLeaveAllocation;
import com.itsdev.payroll.repository.employee.BasicDetailsRepository;
import com.itsdev.payroll.repository.leave.EmployeeLeaveAllocationRepository;
import com.itsdev.payroll.repository.leave.EmployeeLeaveBalanceConsumptionRepository;
import com.itsdev.payroll.serviceimpl.leave.EmployeeLeaveAllocationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class LeaveAllocationImportTest {

    @Mock
    private EmployeeLeaveAllocationRepository allocationRepository;

    @Mock
    private BasicDetailsRepository basicDetailsRepository;

    @Mock
    private EmployeeLeaveBalanceConsumptionRepository consumptionRepository;

    @InjectMocks
    private EmployeeLeaveAllocationServiceImpl service;

    private BasicDetails testEmployee;

    @BeforeEach
    void setUp() {
        testEmployee = new BasicDetails();
        testEmployee.setEmployeeId("HR345");
        testEmployee.setEmployeeNumber("HR345");
    }

    @Test
    void testInheritedFormat_FromScreenshot() {
        when(basicDetailsRepository.findByOrganization_OrganizationIdAndIsDeletedFalse("ORG1"))
                .thenReturn(List.of(testEmployee));
        when(allocationRepository.findByOrganizationId("ORG1"))
                .thenReturn(Collections.emptyList());

        int currentYear = LocalDate.now().getYear();
        String csvContent = "Employee ID,year,Leave Type,Anual Days,Expairation Date,Carry Forword\n" +
                "HR345," + currentYear + ",sick Leave,4,25-12-" + currentYear + ",yes\n" +
                ",,casual Leave,5,25-12-" + currentYear + ",NO\n";

        MockMultipartFile file = new MockMultipartFile(
                "file", "allocations.csv", "text/csv", csvContent.getBytes(StandardCharsets.UTF_8));

        LeaveAllocationImportResultDTO result = service.importNewAllocations("ORG1", file, "Admin");

        assertNotNull(result);
        assertEquals(2, result.getTotalRowsProcessed());
        assertEquals(2, result.getSuccessCount());
        assertEquals(0, result.getFailureCount());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<EmployeeLeaveAllocation>> captor = ArgumentCaptor.forClass(List.class);
        verify(allocationRepository, times(1)).saveAll(captor.capture());

        List<EmployeeLeaveAllocation> saved = captor.getValue();
        assertEquals(2, saved.size());

        EmployeeLeaveAllocation sick = saved.stream().filter(a -> a.getLeaveType().equalsIgnoreCase("sick Leave")).findFirst().orElse(null);
        assertNotNull(sick);
        assertEquals("HR345", sick.getEmployeeId());
        assertEquals(String.valueOf(currentYear), sick.getYear());
        assertEquals(4, sick.getAnnualDays());
        assertEquals(LocalDate.of(currentYear, 12, 25), sick.getExpirationDate());
        assertTrue(sick.getCarryForward());

        EmployeeLeaveAllocation casual = saved.stream().filter(a -> a.getLeaveType().equalsIgnoreCase("casual Leave")).findFirst().orElse(null);
        assertNotNull(casual);
        assertEquals("HR345", casual.getEmployeeId());
        assertEquals(String.valueOf(currentYear), casual.getYear());
        assertEquals(5, casual.getAnnualDays());
        assertEquals(LocalDate.of(currentYear, 12, 25), casual.getExpirationDate());
        assertFalse(casual.getCarryForward());
    }

    @Test
    void testPastExpirationDate_Rejected() {
        when(basicDetailsRepository.findByOrganization_OrganizationIdAndIsDeletedFalse("ORG1"))
                .thenReturn(List.of(testEmployee));
        when(allocationRepository.findByOrganizationId("ORG1"))
                .thenReturn(Collections.emptyList());

        String csvContent = "Employee ID,year,Leave Type,Anual Days,Expairation Date,Carry Forword\n" +
                "HR345,2024,sick Leave,4,25-12-2024,yes\n";

        MockMultipartFile file = new MockMultipartFile(
                "file", "allocations.csv", "text/csv", csvContent.getBytes(StandardCharsets.UTF_8));

        LeaveAllocationImportResultDTO result = service.importNewAllocations("ORG1", file, "Admin");

        assertNotNull(result);
        assertEquals(1, result.getFailureCount());
        assertEquals(0, result.getSuccessCount());
        assertTrue(result.getErrors().get(0).getErrorMessage().contains("cannot be in the past"));
    }

    @Test
    void testExpirationDateOutsideYear_Rejected() {
        when(basicDetailsRepository.findByOrganization_OrganizationIdAndIsDeletedFalse("ORG1"))
                .thenReturn(List.of(testEmployee));
        when(allocationRepository.findByOrganizationId("ORG1"))
                .thenReturn(Collections.emptyList());

        int nextYear = LocalDate.now().getYear() + 1;
        int outsideYear = nextYear + 1;
        String csvContent = "Employee ID,year,Leave Type,Anual Days,Expairation Date,Carry Forword\n" +
                "HR345," + nextYear + ",sick Leave,4,15-01-" + outsideYear + ",yes\n";

        MockMultipartFile file = new MockMultipartFile(
                "file", "allocations.csv", "text/csv", csvContent.getBytes(StandardCharsets.UTF_8));

        LeaveAllocationImportResultDTO result = service.importNewAllocations("ORG1", file, "Admin");

        assertNotNull(result);
        assertEquals(1, result.getFailureCount());
        assertEquals(0, result.getSuccessCount());
        assertTrue(result.getErrors().get(0).getErrorMessage().contains("must fall within the allocation year"));
    }

    @Test
    void testMissingExpirationDate_Rejected() {
        when(basicDetailsRepository.findByOrganization_OrganizationIdAndIsDeletedFalse("ORG1"))
                .thenReturn(List.of(testEmployee));
        when(allocationRepository.findByOrganizationId("ORG1"))
                .thenReturn(Collections.emptyList());

        int currentYear = LocalDate.now().getYear();
        String csvContent = "Employee ID,year,Leave Type,Anual Days,Expairation Date,Carry Forword\n" +
                "HR345," + currentYear + ",sick Leave,4,,yes\n";

        MockMultipartFile file = new MockMultipartFile(
                "file", "allocations.csv", "text/csv", csvContent.getBytes(StandardCharsets.UTF_8));

        LeaveAllocationImportResultDTO result = service.importNewAllocations("ORG1", file, "Admin");

        assertNotNull(result);
        assertEquals(1, result.getFailureCount());
        assertTrue(result.getErrors().get(0).getErrorMessage().contains("mandatory"));
    }

    @Test
    void testDuplicateInDatabase_Rejected() {
        when(basicDetailsRepository.findByOrganization_OrganizationIdAndIsDeletedFalse("ORG1"))
                .thenReturn(List.of(testEmployee));

        int currentYear = LocalDate.now().getYear();
        EmployeeLeaveAllocation existing = new EmployeeLeaveAllocation();
        existing.setEmployeeId("HR345");
        existing.setLeaveType("sick Leave");
        existing.setYear(String.valueOf(currentYear));

        when(allocationRepository.findByOrganizationId("ORG1"))
                .thenReturn(List.of(existing));

        String csvContent = "Employee ID,year,Leave Type,Anual Days,Expairation Date,Carry Forword\n" +
                "HR345," + currentYear + ",sick Leave,4,25-12-" + currentYear + ",yes\n";

        MockMultipartFile file = new MockMultipartFile(
                "file", "allocations.csv", "text/csv", csvContent.getBytes(StandardCharsets.UTF_8));

        LeaveAllocationImportResultDTO result = service.importNewAllocations("ORG1", file, "Admin");

        assertNotNull(result);
        assertEquals(1, result.getFailureCount());
        assertEquals(0, result.getSuccessCount());
        assertTrue(result.getErrors().get(0).getErrorMessage().contains("already has an allocation for 'sick Leave'"));
    }
}
