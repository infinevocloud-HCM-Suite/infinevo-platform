package com.infinevo.hrms.asset;

import java.time.LocalDate;

public record AssetReturnRequest(LocalDate returnedDate, AssetCondition condition, String notes) {}
