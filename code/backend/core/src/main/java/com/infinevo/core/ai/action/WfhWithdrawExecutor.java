package com.infinevo.core.ai.action;

import com.infinevo.core.ai.AiToolContext;
import com.infinevo.core.wfh.WfhRequestResponse;
import com.infinevo.core.wfh.WfhRequestService;
import org.springframework.stereotype.Component;

/** {@code core.wfh.withdraw} (W-114.17): a pending WFH request of the caller's, {@code PENDING -> WITHDRAWN}. */
@Component
public class WfhWithdrawExecutor extends WfhStatusExecutor {

    public static final String ACTION_CODE = "core.wfh.withdraw";

    public WfhWithdrawExecutor(WfhRequestService wfhRequestService) {
        super(wfhRequestService);
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
    WfhRequestResponse change(AiToolContext ctx, WfhRequestResponse request, String reason) {
        return wfhRequestService.withdraw(ctx.tenantId(), request.id(), ctx.employeeId(), reason);
    }
}
