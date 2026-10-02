package com.infinevo.core.leave;

/**
 * Request payload containing a reason for withdraw or cancel actions (W-16.3, spec section 4).
 */
public record LeaveReasonRequest(String reason) {}
