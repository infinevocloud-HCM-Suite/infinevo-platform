package com.infinevo.hrms.asset;

public record AssetStatusUpdateRequest(AssetStatus status, AssetCondition condition, String notes) {}
