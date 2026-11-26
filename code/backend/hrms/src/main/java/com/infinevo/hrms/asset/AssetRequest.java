package com.infinevo.hrms.asset;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AssetRequest(
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
        AssetCondition itemCondition,
        String notes) {}

