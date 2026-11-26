package com.infinevo.hrms.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.EmployeeService;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AssetAssignmentTest {

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
    @DisplayName("assignAsset transitions status to ASSIGNED and records active assignment")
    void assignAsset_success() {
        UUID assetId = UUID.randomUUID();
        UUID empId = UUID.randomUUID();

        CompanyAsset asset = new CompanyAsset();
        asset.setId(assetId);
        asset.setTenantId(TENANT_ID);
        asset.setAssetTag("LAP-001");
        asset.setAssetName("MacBook Pro 14");
        asset.setCurrentStatus(AssetStatus.AVAILABLE);
        asset.setItemCondition(AssetCondition.NEW);

        when(assetRepository.findByIdAndTenantId(assetId, TENANT_ID)).thenReturn(Optional.of(asset));
        when(assignmentRepository.findByTenantIdAndAssetIdAndIsActiveTrue(TENANT_ID, assetId))
                .thenReturn(Optional.empty());
        when(assignmentRepository.save(any(AssetAssignment.class))).thenAnswer(inv -> {
            AssetAssignment a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            return a;
        });
        when(assetRepository.save(any(CompanyAsset.class))).thenAnswer(inv -> inv.getArgument(0));
        when(employeeService.displayNames(List.of(empId))).thenReturn(java.util.Map.of(empId, "Bob Smith"));

        AssetAssignRequest req =
                new AssetAssignRequest(empId, LocalDate.of(2026, 4, 1), AssetCondition.NEW, "Initial laptop handover");
        AssetResponse response = service.assignAsset(TENANT_ID, assetId, req, "hr-admin");

        assertThat(response).isNotNull();
        assertThat(response.currentStatus()).isEqualTo(AssetStatus.ASSIGNED);
        assertThat(response.activeAssignment()).isNotNull();
        assertThat(response.activeAssignment().employeeId()).isEqualTo(empId);
        assertThat(response.activeAssignment().employeeName()).isEqualTo("Bob Smith");
        assertThat(response.activeAssignment().assignmentCondition()).isEqualTo(AssetCondition.NEW);
        assertThat(response.activeAssignment().isActive()).isTrue();
    }

    @Test
    @DisplayName("assignAsset throws AssetConflictException when asset is already ASSIGNED")
    void assignAsset_alreadyAssigned_throwsConflict() {
        UUID assetId = UUID.randomUUID();
        UUID empId = UUID.randomUUID();

        CompanyAsset asset = new CompanyAsset();
        asset.setId(assetId);
        asset.setTenantId(TENANT_ID);
        asset.setAssetTag("LAP-001");
        asset.setCurrentStatus(AssetStatus.ASSIGNED);

        when(assetRepository.findByIdAndTenantId(assetId, TENANT_ID)).thenReturn(Optional.of(asset));

        AssetAssignRequest req = new AssetAssignRequest(empId, LocalDate.of(2026, 4, 1), AssetCondition.GOOD, null);

        assertThatThrownBy(() -> service.assignAsset(TENANT_ID, assetId, req, "hr-admin"))
                .isInstanceOf(AssetConflictException.class)
                .hasMessageContaining("already assigned");
    }

    @Test
    @DisplayName("assignAsset throws AssetValidationException when asset is UNDER_REPAIR or RETIRED")
    void assignAsset_underRepair_throwsValidation() {
        UUID assetId = UUID.randomUUID();
        UUID empId = UUID.randomUUID();

        CompanyAsset asset = new CompanyAsset();
        asset.setId(assetId);
        asset.setTenantId(TENANT_ID);
        asset.setAssetTag("LAP-001");
        asset.setCurrentStatus(AssetStatus.UNDER_REPAIR);

        when(assetRepository.findByIdAndTenantId(assetId, TENANT_ID)).thenReturn(Optional.of(asset));

        AssetAssignRequest req = new AssetAssignRequest(empId, LocalDate.of(2026, 4, 1), AssetCondition.GOOD, null);

        assertThatThrownBy(() -> service.assignAsset(TENANT_ID, assetId, req, "hr-admin"))
                .isInstanceOf(AssetValidationException.class)
                .hasMessageContaining("not available for assignment");
    }

    @Test
    @DisplayName("assignAsset throws AssetValidationException when employeeId is null")
    void assignAsset_missingEmployeeId_throwsValidation() {
        UUID assetId = UUID.randomUUID();
        CompanyAsset asset = new CompanyAsset();
        asset.setId(assetId);
        asset.setTenantId(TENANT_ID);
        asset.setCurrentStatus(AssetStatus.AVAILABLE);

        when(assetRepository.findByIdAndTenantId(assetId, TENANT_ID)).thenReturn(Optional.of(asset));

        AssetAssignRequest req = new AssetAssignRequest(null, LocalDate.of(2026, 4, 1), AssetCondition.GOOD, null);

        assertThatThrownBy(() -> service.assignAsset(TENANT_ID, assetId, req, "hr-admin"))
                .isInstanceOf(AssetValidationException.class)
                .hasMessageContaining("Employee ID is required");
    }
}

