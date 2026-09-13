package com.itsdev.payroll.service.dashboard;

import com.itsdev.payroll.dto.dashboard.DashboardResponse;
import com.itsdev.payroll.dto.dashboard.StatutorySummaryDTO;
import com.itsdev.payroll.dto.dashboard.TdsSummaryDTO;

import java.time.LocalDate;

/**
 * DashboardService provides summarized payroll and statutory information
 * for the organization's main dashboard view.
 *
 * All methods are organization-scoped, using organizationId from request header.
 */
public interface DashboardService {

    /**
     * Fetches the complete dashboard summary including:
     * - Employee summary (active & unfinished)
     * - Current PayRun summary
     * - Bank mismatch / direct deposit status
     * - Monthly payroll chart summary
     *
     * @param organizationId The organization identifier (from request header)
     * @return DashboardResponse containing all dashboard-level data
     */
    DashboardResponse getDashboardSummary(String organizationId);


    /**
     * Fetches statutory contribution summary for EPF or ESI for the given organization.
     * Optionally filtered by a date range.
     *
     * @param organizationId The organization identifier (from request header)
     * @param type Statutory type: "epf" or "esi"
     * @param fromDate Optional start date for calculation (can be null)
     * @param toDate Optional end date for calculation (can be null)
     * @return StatutorySummaryDTO containing employee + employer contributions and total
     */
    StatutorySummaryDTO getStatutorySummary(String organizationId, String type, LocalDate fromDate, LocalDate toDate);


    /**
     * Fetches TDS summary (total deducted tax) for the given date range and organization.
     *
     * @param organizationId The organization identifier (from request header)
     * @param fromDate Optional start date for range (can be null)
     * @param toDate Optional end date for range (can be null)
     * @return TdsSummaryDTO containing total tax deducted
     */
    TdsSummaryDTO getTdsSummary(String organizationId, LocalDate fromDate, LocalDate toDate);
}
