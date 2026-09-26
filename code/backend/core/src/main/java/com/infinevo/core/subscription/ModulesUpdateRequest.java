package com.infinevo.core.subscription;

import com.infinevo.shared.entitlement.PlatformModule;
import java.util.Set;

/**
 * Request payload for updating a tenant's subscription modules (W-12.1).
 */
public record ModulesUpdateRequest(Set<PlatformModule> modules) {}
