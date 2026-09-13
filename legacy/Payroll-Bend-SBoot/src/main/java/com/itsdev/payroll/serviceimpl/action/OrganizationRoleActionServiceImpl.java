package com.itsdev.payroll.serviceimpl.action;

import com.itsdev.payroll.entity.auth.Action;
import com.itsdev.payroll.entity.auth.OrganizationRoleAction;
import com.itsdev.payroll.entity.organization.OrganizationRole;
import com.itsdev.payroll.repository.auth.ActionRepository;
import com.itsdev.payroll.repository.auth.OrganizationRoleActionRepository;
import com.itsdev.payroll.repository.organization.OrganizationRoleRepository;
import com.itsdev.payroll.service.action.OrganizationRoleActionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class OrganizationRoleActionServiceImpl implements OrganizationRoleActionService {

    private final OrganizationRoleRepository roleRepo;
    private final ActionRepository actionRepo;
    private final OrganizationRoleActionRepository orgRoleActionRepo;

    public OrganizationRoleActionServiceImpl(OrganizationRoleRepository roleRepo,
            ActionRepository actionRepo,
            OrganizationRoleActionRepository orgRoleActionRepo) {
        this.roleRepo = roleRepo;
        this.actionRepo = actionRepo;
        this.orgRoleActionRepo = orgRoleActionRepo;
    }

    @Override
    @Transactional
    public List<String> assignActionsToRole(Long organizationRoleDbId, List<Long> actionIds, String performedBy) {
        OrganizationRole role = roleRepo.findById(organizationRoleDbId)
                .orElseThrow(() -> new RuntimeException("Role not found id=" + organizationRoleDbId));

        List<String> assignedCodes = new ArrayList<>();
        for (Long actionId : actionIds) {
            Optional<Action> actionOpt = actionRepo.findById(actionId);
            if (actionOpt.isEmpty())
                continue;
            Action action = actionOpt.get();

            // check if mapping exists
            boolean exists = orgRoleActionRepo.findByOrganizationRole_Id(organizationRoleDbId).stream()
                    .anyMatch(m -> m.getAction().getId().equals(actionId));
            if (exists) {
                assignedCodes.add(action.getCode());
                continue;
            }

            OrganizationRoleAction m = new OrganizationRoleAction();
            m.setOrganizationRole(role);
            m.setAction(action);
            m.setCreatedBy(performedBy);
            orgRoleActionRepo.save(m);
            assignedCodes.add(action.getCode());
        }

        // Invalidate cache for all users who have this role (simple approach)
        // We'll find mappings from OrganizationUserRoleMapping and call
        // authzService.invalidate...
        // To keep Service-level coupling small, we'll leave cache invalidation to
        // controller after assignment
        return assignedCodes;
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getActionCodesForRole(Long organizationRoleDbId) {
        List<OrganizationRoleAction> list = orgRoleActionRepo.findByOrganizationRole_Id(organizationRoleDbId);
        List<String> codes = new ArrayList<>();
        for (OrganizationRoleAction m : list)
            codes.add(m.getAction().getCode());
        return codes;
    }

    @Override
    @Transactional
    public void removeActionFromRole(Long organizationRoleDbId, Long actionId, String performedBy) {
        orgRoleActionRepo.deleteByOrganizationRole_IdAndAction_Id(organizationRoleDbId, actionId);

        // cache invalidation left to controller (we will provide helper)
    }
}
