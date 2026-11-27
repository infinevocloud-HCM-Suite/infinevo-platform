package com.infinevo.core.ai.action;

import com.infinevo.core.ai.AiToolContext;
import com.infinevo.core.wfh.WfhRequestResponse;
import com.infinevo.core.wfh.WfhRequestService;
import org.springframework.stereotype.Component;

/**
 * {@code core.wfh.cancel} (W-114.17): an approved WFH request of the caller's that has not started, {@code APPROVED
 * -> CANCELLED}. The WFH service refuses on or after the start date and reverses the consumed days.
 */
@Component
public class WfhCancelExecutor extends WfhStatusExecutor {

    public static final String ACTION_CODE = "core.wfh.cancel";

    public WfhCancelExecutor(WfhRequestService wfhRequestService) {
        super(wfhRequestService);
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
    WfhRequestResponse change(AiToolContext ctx, WfhRequestResponse request, String reason) {
        return wfhRequestService.cancel(ctx.tenantId(), request.id(), ctx.employeeId(), reason);
    }
}
