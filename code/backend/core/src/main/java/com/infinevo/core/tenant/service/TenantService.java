package com.infinevo.core.tenant.service;

import com.infinevo.core.tenant.dto.CreateOrganisationRequest;
import com.infinevo.core.tenant.dto.OrganisationResponse;
import java.util.UUID;

public interface TenantService {
    OrganisationResponse createOrganisation(CreateOrganisationRequest request, UUID creatorUserId);
}
