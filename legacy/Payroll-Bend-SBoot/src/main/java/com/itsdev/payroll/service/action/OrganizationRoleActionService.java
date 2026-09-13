package com.itsdev.payroll.service.action;

import java.util.List;

public interface OrganizationRoleActionService {
    List<String> assignActionsToRole(Long organizationRoleDbId, List<Long> actionIds, String performedBy);

    List<String> getActionCodesForRole(Long organizationRoleDbId);

    void removeActionFromRole(Long organizationRoleDbId, Long actionId, String performedBy);
}
