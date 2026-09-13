package com.itsdev.payroll.serviceimpl.auth;

import com.itsdev.payroll.entity.OrganizationUserRoleMapping;
import com.itsdev.payroll.repository.OrganizationUserRoleMappingRepository;
import com.itsdev.payroll.repository.auth.OrganizationRoleActionRepository;
import com.itsdev.payroll.repository.organization.OrganizationRoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

interface AuthzService {
    Set<String> getAllowedActionsForUser(String userId, String organizationId);

    boolean canPerform(String userId, String organizationId, String actionKey);

    void invalidateUserOrgCache(String userId, String organizationId);
}

@Service
public class AuthzServiceImpl implements com.itsdev.payroll.service.auth.AuthzService {

    private final OrganizationUserRoleMappingRepository userRoleMappingRepo;
    private final OrganizationRoleRepository organizationRoleRepository;
    private final OrganizationRoleActionRepository orgRoleActionRepo;

    // Simple in-memory cache: Map<userId::orgId, Set<actionKey>>
    private final Map<String, Set<String>> cache = new ConcurrentHashMap<>();

    public AuthzServiceImpl(OrganizationUserRoleMappingRepository userRoleMappingRepo,
            OrganizationRoleRepository organizationRoleRepository,
            OrganizationRoleActionRepository orgRoleActionRepo) {
        this.userRoleMappingRepo = userRoleMappingRepo;
        this.organizationRoleRepository = organizationRoleRepository;
        this.orgRoleActionRepo = orgRoleActionRepo;
    }

    private String cacheKey(String userId, String orgId) {
        return userId + "::" + orgId;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> getAllowedActionsForUser(String userId, String organizationId) {
        String key = cacheKey(userId, organizationId);
        Set<String> cached = cache.get(key);
        if (cached != null)
            return cached;
        // fetch all mappings for this user and org
        Optional<OrganizationUserRoleMapping> mapping = userRoleMappingRepo.findByUserIdAndOrganizationId(userId,
                organizationId);
        if (!mapping.isPresent()) {
            cache.put(key, Collections.emptySet());
            return Collections.emptySet();
        }

        // extract roleIds (roleId string is stored in mapping)
        List<String> roleIds = new ArrayList<>();
        OrganizationUserRoleMapping m = mapping.get();
        if (m.getRoleId() != null)
            roleIds.add(m.getRoleId());

        // convert roleId (string) -> OrganizationRole.id (Long)
        List<Long> orgRoleDbIds = new ArrayList<>();
        for (String rid : roleIds) {
            organizationRoleRepository.findByRoleIdAndIsDeletedFalse(rid)
                    .ifPresent(r -> orgRoleDbIds.add(r.getId()));
        }

        if (orgRoleDbIds.isEmpty()) {
            cache.put(key, Collections.emptySet());
            return Collections.emptySet();
        }

        // query role->action codes
        List<String> actionCodes = orgRoleActionRepo.findActionCodesByOrganizationRoleIds(orgRoleDbIds);
        Set<String> result = new HashSet<>(actionCodes);
        cache.put(key, result);
        return result;
    }

    @Override
    public boolean canPerform(String userId, String organizationId, String actionKey) {
        if (userId == null || organizationId == null || actionKey == null)
            return false;
        Set<String> actions = getAllowedActionsForUser(userId, organizationId);
        return actions.contains(actionKey);
    }

    @Override
    public void invalidateUserOrgCache(String userId, String organizationId) {
        cache.remove(cacheKey(userId, organizationId));
    }
}
