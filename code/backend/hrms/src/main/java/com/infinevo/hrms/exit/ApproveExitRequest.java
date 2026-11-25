package com.infinevo.hrms.exit;

import java.time.LocalDate;

/**
 * Manager/HR approval payload allowing optional revision of the last working day (W-103).
 */
public record ApproveExitRequest(LocalDate approvedLwd) {}
