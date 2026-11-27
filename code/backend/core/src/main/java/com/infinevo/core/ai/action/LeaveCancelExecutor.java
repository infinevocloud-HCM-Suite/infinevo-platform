package com.infinevo.core.ai.action;

import com.infinevo.core.ai.AiToolContext;
import com.infinevo.core.leave.LeaveRequestResponse;
import com.infinevo.core.leave.LeaveRequestService;
import org.springframework.stereotype.Component;

/**
 * {@code core.leave.cancel} (W-114.17): an approved leave of the caller's that has not started, {@code APPROVED ->
 * CANCELLED}. The leave service releases the days and refuses on or after the start date.
 */
@Component
public class LeaveCancelExecutor extends LeaveStatusExecutor {

    public static final String ACTION_CODE = "core.leave.cancel";

    public LeaveCancelExecutor(LeaveRequestService leaveRequestService) {
        super(leaveRequestService);
    }

    @Override
    public String actionCode() {
        return ACTION_CODE;
    }

    @Override
    String verb() {
        return "Cancel";
    }

    @Override
    LeaveRequestResponse change(AiToolContext ctx, LeaveRequestResponse request, String reason) {
        return leaveRequestService.cancel(ctx.tenantId(), request.id(), ctx.employeeId(), reason);
    }
}
