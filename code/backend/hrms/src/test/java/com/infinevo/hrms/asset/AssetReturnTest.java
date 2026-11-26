package com.infinevo.hrms.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.infinevo.core.employee.EmployeeService;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AssetReturnTest {

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
    @DisplayName("returnAsset transitions status back to AVAILABLE and deactivates assignment")
    void returnAsset_success() {
        UUID assetId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();

        CompanyAsset asset = new CompanyAsset();
        asset.setId(assetId);
        asset.setTenantId(TENANT_ID);
        asset.setAssetTag("LAP-001");
        asset.setCurrentStatus(AssetStatus.ASSIGNED);
        asset.setItemCondition(AssetCondition.NEW);

        AssetAssignment activeAssignment = new AssetAssignment();
        activeAssignment.setId(assignmentId);
        activeAssignment.setTenantId(TENANT_ID);
        activeAssignment.setAssetId(assetId);
        activeAssignment.setEmployeeId(UUID.randomUUID());
        activeAssignment.setAssignedDate(LocalDate.of(2026, 1, 10));
        activeAssignment.setActive(true);
        activeAssignment.setAssignmentCondition(AssetCondition.NEW);

        when(assetRepository.findByIdAndTenantId(assetId, TENANT_ID)).thenReturn(Optional.of(asset));
        when(assignmentRepository.findByTenantIdAndAssetIdAndIsActiveTrue(TENANT_ID, assetId))
                .thenReturn(Optional.of(activeAssignment));
        when(assignmentRepository.save(any(AssetAssignment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(assetRepository.save(any(CompanyAsset.class))).thenAnswer(inv -> inv.getArgument(0));

        AssetReturnRequest returnReq = new AssetReturnRequest(
                LocalDate.of(2026, 4, 15), AssetCondition.GOOD, "Returned upon project completion");

        AssetResponse returned = service.returnAsset(TENANT_ID, assetId, returnReq, "it-admin");

        assertThat(returned).isNotNull();
        assertThat(returned.currentStatus()).isEqualTo(AssetStatus.AVAILABLE);
        assertThat(returned.itemCondition()).isEqualTo(AssetCondition.GOOD);
        assertThat(returned.activeAssignment()).isNull();

        assertThat(activeAssignment.isActive()).isFalse();
        assertThat(activeAssignment.getReturnedDate()).isEqualTo(LocalDate.of(2026, 4, 15));
        assertThat(activeAssignment.getReturnedBy()).isEqualTo("it-admin");
        assertThat(activeAssignment.getReturnCondition()).isEqualTo(AssetCondition.GOOD);
        assertThat(activeAssignment.getReturnNotes()).isEqualTo("Returned upon project completion");
    }

    @Test
    @DisplayName("returnAsset throws AssetValidationException when asset is not ASSIGNED")
    void returnAsset_notAssigned_throwsValidation() {
        UUID assetId = UUID.randomUUID();
        CompanyAsset asset = new CompanyAsset();
        asset.setId(assetId);
        asset.setTenantId(TENANT_ID);
        asset.setAssetTag("LAP-001");
        asset.setCurrentStatus(AssetStatus.AVAILABLE);

        when(assetRepository.findByIdAndTenantId(assetId, TENANT_ID)).thenReturn(Optional.of(asset));

        AssetReturnRequest returnReq = new AssetReturnRequest(LocalDate.of(2026, 4, 15), AssetCondition.GOOD, null);

        assertThatThrownBy(() -> service.returnAsset(TENANT_ID, assetId, returnReq, "it-admin"))
                .isInstanceOf(AssetValidationException.class)
                .hasMessageContaining("not currently assigned");
    }

    @Test
    @DisplayName("returnAsset throws AssetValidationException when return date is before assigned date")
    void returnAsset_dateBeforeAssignedDate_throwsValidation() {
        UUID assetId = UUID.randomUUID();
        CompanyAsset asset = new CompanyAsset();
        asset.setId(assetId);
        asset.setTenantId(TENANT_ID);
        asset.setAssetTag("LAP-001");
        asset.setCurrentStatus(AssetStatus.ASSIGNED);

        AssetAssignment active = new AssetAssignment();
        active.setAssignedDate(LocalDate.of(2026, 4, 1));

        when(assetRepository.findByIdAndTenantId(assetId, TENANT_ID)).thenReturn(Optional.of(asset));
        when(assignmentRepository.findByTenantIdAndAssetIdAndIsActiveTrue(TENANT_ID, assetId))
                .thenReturn(Optional.of(active));

        AssetReturnRequest returnReq =
                new AssetReturnRequest(LocalDate.of(2026, 3, 1), AssetCondition.GOOD, "Wrong date");

        assertThatThrownBy(() -> service.returnAsset(TENANT_ID, assetId, returnReq, "admin"))
                .isInstanceOf(AssetValidationException.class)
                .hasMessageContaining("Return date cannot be before assignment date");
    }
}

