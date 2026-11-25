package com.infinevo.hrms.exit;

/**
 * Request payload to clear or waive a departmental clearance item or whole department (W-103).
 */
public record UpdateClearanceRequest(ClearanceItemStatus status, String remarks) {}
