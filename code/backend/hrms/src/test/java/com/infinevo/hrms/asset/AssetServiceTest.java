package com.infinevo.hrms.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.EmployeeService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AssetServiceTest {

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
    @DisplayName("createAsset registers new asset with AVAILABLE status")
    void createAsset_success() {
        AssetRequest req = new AssetRequest(
                "LAP-001",
                "MacBook Pro 14",
                AssetCategory.LAPTOP,
                "Apple",
                "A2442",
                "SN12345678",
                LocalDate.of(2026, 1, 15),
                new BigDecimal("1999.00"),
                LocalDate.of(2028, 1, 15),
                "Apple Direct",
                AssetCondition.NEW,
                "Brand new laptop");

        when(assetRepository.existsByTenantIdAndAssetTag(TENANT_ID, "LAP-001")).thenReturn(false);
        when(assetRepository.save(any(CompanyAsset.class))).thenAnswer(inv -> {
            CompanyAsset ca = inv.getArgument(0);
            ca.setId(UUID.randomUUID());
            return ca;
        });

        AssetResponse response = service.createAsset(TENANT_ID, req, "admin");

        assertThat(response).isNotNull();
        assertThat(response.assetTag()).isEqualTo("LAP-001");
        assertThat(response.assetName()).isEqualTo("MacBook Pro 14");
        assertThat(response.category()).isEqualTo(AssetCategory.LAPTOP);
        assertThat(response.currentStatus()).isEqualTo(AssetStatus.AVAILABLE);
        assertThat(response.itemCondition()).isEqualTo(AssetCondition.NEW);
    }

    @Test
    @DisplayName("createAsset throws AssetConflictException when assetTag already exists")
    void createAsset_duplicateTag_throwsConflict() {
        AssetRequest req = new AssetRequest(
                "LAP-001",
                "MacBook Pro 14",
                AssetCategory.LAPTOP,
                "Apple",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);

        when(assetRepository.existsByTenantIdAndAssetTag(TENANT_ID, "LAP-001")).thenReturn(true);

        assertThatThrownBy(() -> service.createAsset(TENANT_ID, req, "admin"))
                .isInstanceOf(AssetConflictException.class)
                .hasMessageContaining("Asset with tag 'LAP-001' already exists");
    }

    @Test
    @DisplayName("createAsset throws validation exception when tag or name is blank")
    void createAsset_blankFields_throwsValidation() {
        AssetRequest noTag = new AssetRequest(
                "", "Name", AssetCategory.LAPTOP, null, null, null, null, null, null, null, null, null);
        assertThatThrownBy(() -> service.createAsset(TENANT_ID, noTag, "admin"))
                .isInstanceOf(AssetValidationException.class);

        AssetRequest noName =
                new AssetRequest("TAG", "", AssetCategory.LAPTOP, null, null, null, null, null, null, null, null, null);
        assertThatThrownBy(() -> service.createAsset(TENANT_ID, noName, "admin"))
                .isInstanceOf(AssetValidationException.class);
    }

    @Test
    @DisplayName("updateStatus updates status and itemCondition")
    void updateStatus_success() {
        UUID assetId = UUID.randomUUID();
        CompanyAsset asset = new CompanyAsset();
        asset.setId(assetId);
        asset.setTenantId(TENANT_ID);
        asset.setAssetTag("MON-001");
        asset.setAssetName("Dell 27 Monitor");
        asset.setCurrentStatus(AssetStatus.AVAILABLE);
        asset.setItemCondition(AssetCondition.GOOD);

        when(assetRepository.findByIdAndTenantId(assetId, TENANT_ID)).thenReturn(Optional.of(asset));
        when(assetRepository.save(any(CompanyAsset.class))).thenAnswer(inv -> inv.getArgument(0));

        AssetStatusUpdateRequest req =
                new AssetStatusUpdateRequest(AssetStatus.UNDER_REPAIR, AssetCondition.DAMAGED, "Flickering screen");
        AssetResponse updated = service.updateStatus(TENANT_ID, assetId, req, "tech");

        assertThat(updated.currentStatus()).isEqualTo(AssetStatus.UNDER_REPAIR);
        assertThat(updated.itemCondition()).isEqualTo(AssetCondition.DAMAGED);
    }

    @Test
    @DisplayName("updateStatus rejects manual setting of ASSIGNED status")
    void updateStatus_cannotSetAssignedDirectly() {
        UUID assetId = UUID.randomUUID();
        CompanyAsset asset = new CompanyAsset();
        asset.setId(assetId);
        asset.setTenantId(TENANT_ID);
        asset.setCurrentStatus(AssetStatus.AVAILABLE);

        when(assetRepository.findByIdAndTenantId(assetId, TENANT_ID)).thenReturn(Optional.of(asset));

        AssetStatusUpdateRequest req = new AssetStatusUpdateRequest(AssetStatus.ASSIGNED, null, null);
        assertThatThrownBy(() -> service.updateStatus(TENANT_ID, assetId, req, "admin"))
                .isInstanceOf(AssetValidationException.class)
                .hasMessageContaining("Cannot set status to ASSIGNED directly");
    }

    @Test
    @DisplayName("getAsset returns asset details with active assignment if present")
    void getAsset_success() {
        UUID assetId = UUID.randomUUID();
        UUID empId = UUID.randomUUID();

        CompanyAsset asset = new CompanyAsset();
        asset.setId(assetId);
        asset.setTenantId(TENANT_ID);
        asset.setAssetTag("LAP-002");
        asset.setAssetName("ThinkPad X1");
        asset.setCurrentStatus(AssetStatus.ASSIGNED);

        AssetAssignment assignment = new AssetAssignment();
        assignment.setId(UUID.randomUUID());
        assignment.setTenantId(TENANT_ID);
        assignment.setAssetId(assetId);
        assignment.setEmployeeId(empId);
        assignment.setAssignedDate(LocalDate.of(2026, 4, 1));
        assignment.setAssignedBy("hr");
        assignment.setActive(true);

        when(assetRepository.findByIdAndTenantId(assetId, TENANT_ID)).thenReturn(Optional.of(asset));
        when(assignmentRepository.findByTenantIdAndAssetIdAndIsActiveTrue(TENANT_ID, assetId))
                .thenReturn(Optional.of(assignment));
        when(employeeService.displayNames(List.of(empId))).thenReturn(java.util.Map.of(empId, "Alice Johnson"));

        AssetResponse resp = service.getAsset(TENANT_ID, assetId);

        assertThat(resp).isNotNull();
        assertThat(resp.assetTag()).isEqualTo("LAP-002");
        assertThat(resp.activeAssignment()).isNotNull();
        assertThat(resp.activeAssignment().employeeName()).isEqualTo("Alice Johnson");
    }
}
