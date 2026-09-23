package com.infinevo.core.tenant.dto;

import com.infinevo.shared.entitlement.ModuleCode;
import java.util.List;

public record CreateOrganisationRequest(String name, List<ModuleCode> modules) {}
