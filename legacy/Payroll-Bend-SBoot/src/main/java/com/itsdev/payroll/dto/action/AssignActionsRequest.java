package com.itsdev.payroll.dto.action;

import java.util.List;

public class AssignActionsRequest {
    private List<Long> actionIds;

    public List<Long> getActionIds() {
        return actionIds;
    }

    public void setActionIds(List<Long> actionIds) {
        this.actionIds = actionIds;
    }
}
