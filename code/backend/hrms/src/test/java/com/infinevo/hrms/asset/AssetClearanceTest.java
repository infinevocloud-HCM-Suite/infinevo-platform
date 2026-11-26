package com.infinevo.hrms.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.EmployeeService;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AssetClearanceTest {

    private static final UUID TENANT_ID = UUID.randomUUID();

    private CompanyAssetRepository assetRepository;
    private AssetAssignmentRepository assignmentRepository;
    private EmployeeService employeeService;
    private AssetServiceImpl service;

    @BeforeEach
    void setUp() {
        assetRepository = mock(CompanyAssetRepository.class);
        assignmentRepository = mock(AssetAssignmentRepository.class);
        employeeService = mock(EmployeeService.class);
        service = new AssetServiceImpl(assetRepository, assignmentRepository, employeeService);
    }

    @Test
    @DisplayName("getEmployeeClearance returns CLEAR when employee has no active equipment assigned")
    void getEmployeeClearance_clear_whenZeroAssets() {
        UUID employeeId = UUID.randomUUID();

        when(assignmentRepository.findByTenantIdAndEmployeeIdAndIsActiveTrue(TENANT_ID, employeeId))
                .thenReturn(Collections.emptyList());

        EmployeeClearanceResponse response = service.getEmployeeClearance(TENANT_ID, employeeId);

        assertThat(response).isNotNull();
        assertThat(response.employeeId()).isEqualTo(employeeId);
        assertThat(response.clearanceStatus()).isEqualTo(ClearanceStatus.CLEAR);
        assertThat(response.activeAssetCount()).isEqualTo(0);
        assertThat(response.activeAssets()).isEmpty();
    }

    @Test
    @DisplayName("getEmployeeClearance returns PENDING_RETURN when employee holds active hardware")
    void getEmployeeClearance_pendingReturn_whenHoldingAssets() {
        UUID employeeId = UUID.randomUUID();
        UUID laptopId = UUID.randomUUID();
        UUID monitorId = UUID.randomUUID();

        CompanyAsset laptop = new CompanyAsset();
        laptop.setId(laptopId);
        laptop.setTenantId(TENANT_ID);
        laptop.setAssetTag("LAP-101");
        laptop.setAssetName("MacBook Pro");
        laptop.setCategory(AssetCategory.LAPTOP);
        laptop.setCurrentStatus(AssetStatus.ASSIGNED);

        CompanyAsset monitor = new CompanyAsset();
        monitor.setId(monitorId);
        monitor.setTenantId(TENANT_ID);
        monitor.setAssetTag("MON-201");
        monitor.setAssetName("Dell 27 Monitor");
        monitor.setCategory(AssetCategory.MONITOR);
        monitor.setCurrentStatus(AssetStatus.ASSIGNED);

        AssetAssignment assign1 = new AssetAssignment();
        assign1.setId(UUID.randomUUID());
        assign1.setTenantId(TENANT_ID);
        assign1.setAssetId(laptopId);
        assign1.setEmployeeId(employeeId);
        assign1.setAssignedDate(LocalDate.of(2026, 1, 15));
        assign1.setActive(true);

        AssetAssignment assign2 = new AssetAssignment();
        assign2.setId(UUID.randomUUID());
        assign2.setTenantId(TENANT_ID);
        assign2.setAssetId(monitorId);
        assign2.setEmployeeId(employeeId);
        assign2.setAssignedDate(LocalDate.of(2026, 2, 1));
        assign2.setActive(true);

        when(assignmentRepository.findByTenantIdAndEmployeeIdAndIsActiveTrue(TENANT_ID, employeeId))
                .thenReturn(List.of(assign1, assign2));
        when(assetRepository.findByTenantIdAndIdIn(TENANT_ID, List.of(laptopId, monitorId)))
                .thenReturn(List.of(laptop, monitor));
        when(employeeService.displayNames(List.of(employeeId)))
                .thenReturn(java.util.Map.of(employeeId, "Charlie Brown"));

        EmployeeClearanceResponse response = service.getEmployeeClearance(TENANT_ID, employeeId);

        assertThat(response).isNotNull();
        assertThat(response.employeeId()).isEqualTo(employeeId);
        assertThat(response.clearanceStatus()).isEqualTo(ClearanceStatus.PENDING_RETURN);
        assertThat(response.activeAssetCount()).isEqualTo(2);
        assertThat(response.activeAssets()).hasSize(2);
        assertThat(response.activeAssets().stream().map(AssetResponse::assetTag))
                .containsExactlyInAnyOrder("LAP-101", "MON-201");
    }
}

