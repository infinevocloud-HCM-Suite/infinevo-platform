package com.infinevo.core.tenant.dto;

import com.infinevo.shared.entitlement.ModuleCode;
import java.util.List;
import java.util.UUID;

public record OrganisationResponse(UUID tenantId, String name, String status, List<ModuleCode> modules) {}
