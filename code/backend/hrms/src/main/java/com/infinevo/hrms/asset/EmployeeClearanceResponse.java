package com.infinevo.hrms.asset;

import java.util.List;
import java.util.UUID;

public record EmployeeClearanceResponse(
        UUID employeeId, ClearanceStatus clearanceStatus, int activeAssetCount, List<AssetResponse> activeAssets) {}
