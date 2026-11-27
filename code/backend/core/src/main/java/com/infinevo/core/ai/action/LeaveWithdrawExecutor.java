package com.infinevo.core.ai.action;

import com.infinevo.core.ai.AiToolContext;
import com.infinevo.core.leave.LeaveRequestResponse;
import com.infinevo.core.leave.LeaveRequestService;
import org.springframework.stereotype.Component;

/** {@code core.leave.withdraw} (W-114.17): a pending leave request of the caller's, {@code PENDING -> WITHDRAWN}. */
@Component
public class LeaveWithdrawExecutor extends LeaveStatusExecutor {

    public static final String ACTION_CODE = "core.leave.withdraw";

    public LeaveWithdrawExecutor(LeaveRequestService leaveRequestService) {
        super(leaveRequestService);
    }

    @Override
    public String actionCode() {
        return ACTION_CODE;
    }

    @Override
    String verb() {
        return "Withdraw";
    }

    @Override
    LeaveRequestResponse change(AiToolContext ctx, LeaveRequestResponse request, String reason) {
        return leaveRequestService.withdraw(ctx.tenantId(), request.id(), ctx.employeeId(), reason);
    }
}
