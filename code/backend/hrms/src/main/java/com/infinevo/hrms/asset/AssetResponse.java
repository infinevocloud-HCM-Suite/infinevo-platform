package com.infinevo.hrms.asset;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AssetResponse(
        UUID id,
        String assetTag,
        String assetName,
        AssetCategory category,
        String brand,
        String modelNumber,
        String serialNumber,
        LocalDate purchaseDate,
        BigDecimal purchaseCost,
        LocalDate warrantyExpiry,
        String vendorName,
        AssetStatus currentStatus,
        AssetCondition itemCondition,
        String notes,
        AssetAssignmentResponse activeAssignment,
        Instant createdAt,
        Instant updatedAt) {}

