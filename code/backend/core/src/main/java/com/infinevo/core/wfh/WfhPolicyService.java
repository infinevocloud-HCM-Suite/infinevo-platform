package com.infinevo.core.wfh;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WfhPolicyService {

    WfhPolicyResponse createPolicy(UUID tenantId, CreateWfhPolicyRequest request);

    Optional<WfhPolicyResponse> getActivePolicy(UUID tenantId);

    List<WfhPolicyResponse> listPolicies(UUID tenantId);

    Optional<WfhPolicyResponse> getPolicyById(UUID tenantId, UUID policyId);

    WfhPolicy getOrCreateDefaultPolicy(UUID tenantId);
}
