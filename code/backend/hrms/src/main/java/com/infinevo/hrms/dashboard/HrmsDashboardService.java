package com.infinevo.hrms.dashboard;

/** The HRMS dashboard for the caller in the bound tenant, read-only (W-44 §4). */
public interface HrmsDashboardService {

    /**
     * The caller's dashboard: each block present only when the caller holds the action that guards the same data
     * elsewhere, {@code null} otherwise.
     *
     * @throws org.springframework.security.access.AccessDeniedException when the login is not linked to an employee
     */
    HrmsDashboardResponse forCaller();
}
