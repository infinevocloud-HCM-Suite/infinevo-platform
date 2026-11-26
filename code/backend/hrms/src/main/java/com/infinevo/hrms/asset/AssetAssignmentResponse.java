package com.infinevo.hrms.asset;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AssetAssignmentResponse(
        UUID id,
        UUID assetId,
        String assetTag,
        String assetName,
        UUID employeeId,
        String employeeName,
        LocalDate assignedDate,
        String assignedBy,
        AssetCondition assignmentCondition,
        String assignmentNotes,
        LocalDate returnedDate,
        String returnedBy,
        AssetCondition returnCondition,
        String returnNotes,
        boolean isActive,
        Instant createdAt) {}

