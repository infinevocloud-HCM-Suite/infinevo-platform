package com.infinevo.hrms.asset;

import java.util.List;
import java.util.UUID;

public interface AssetService {

    List<AssetResponse> listAssets(UUID tenantId, AssetStatus status, AssetCategory category);

    AssetResponse createAsset(UUID tenantId, AssetRequest request, String createdBy);

    AssetResponse getAsset(UUID tenantId, UUID assetId);

    AssetResponse updateAsset(UUID tenantId, UUID assetId, AssetRequest request, String updatedBy);

    AssetResponse updateStatus(UUID tenantId, UUID assetId, AssetStatusUpdateRequest request, String updatedBy);

    AssetResponse assignAsset(UUID tenantId, UUID assetId, AssetAssignRequest request, String assignedBy);

    AssetResponse returnAsset(UUID tenantId, UUID assetId, AssetReturnRequest request, String returnedBy);

    List<AssetAssignmentResponse> getAssignmentHistory(UUID tenantId, UUID assetId);

    EmployeeClearanceResponse getEmployeeClearance(UUID tenantId, UUID employeeId);

    List<AssetResponse> getAssignedAssetsForEmployee(UUID tenantId, UUID employeeId);
}

