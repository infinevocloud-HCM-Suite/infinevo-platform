package com.infinevo.hrms.asset;

import java.time.LocalDate;
import java.util.UUID;

public record AssetAssignRequest(UUID employeeId, LocalDate assignedDate, AssetCondition condition, String notes) {}
