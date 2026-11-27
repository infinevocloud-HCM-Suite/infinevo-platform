package com.infinevo.core.ai.action;

import com.infinevo.core.approval.ApprovalFlowType;
import com.infinevo.core.leave.LeaveRequest;
import com.infinevo.core.leave.LeaveRequestRepository;
import com.infinevo.core.leave.LeaveType;
import com.infinevo.core.leave.LeaveTypeRepository;
import com.infinevo.core.wfh.WfhRequest;
import com.infinevo.core.wfh.WfhRequestRepository;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Reads what an approval step is about, for the AI helper's approval list and card (W-114.18). Leave and work from
 * home live in {@code core}, so their dates, status and the employee's own reason are read from the database. The
 * other decidable flow types live in {@code hrms}, which {@code core} may not read: they carry the inbox's one-line
 * summary only.
 */
@Component
class ApprovalItemReader {

    /** The flow types the helper may decide: the ones with no money attached. */
    static final Set<ApprovalFlowType> DECIDABLE = EnumSet.of(
            ApprovalFlowType.LEAVE,
            ApprovalFlowType.WORK_FROM_HOME,
            ApprovalFlowType.REGULARIZATION,
            ApprovalFlowType.OVERTIME,
            ApprovalFlowType.TIMESHEET);

    static final String OPEN_FOR_NOTE = "Open the request to read the employee's note.";

    private final LeaveRequestRepository leaveRequests;
    private final WfhRequestRepository wfhRequests;
    private final LeaveTypeRepository leaveTypes;

    ApprovalItemReader(
            LeaveRequestRepository leaveRequests, WfhRequestRepository wfhRequests, LeaveTypeRepository leaveTypes) {
        this.leaveRequests = Objects.requireNonNull(leaveRequests, "leaveRequests must not be null");
        this.wfhRequests = Objects.requireNonNull(wfhRequests, "wfhRequests must not be null");
        this.leaveTypes = Objects.requireNonNull(leaveTypes, "leaveTypes must not be null");
    }

    /**
     * What the request is.
     *
     * @param details a plain line: leave type and dates, WFH dates, or the inbox summary
     * @param reason the employee's own words for leave and WFH, in full; {@code null} otherwise
     * @param hasReason whether the reason can be read here at all (false for the {@code hrms} types)
     * @param status the leave or WFH request's own status, or {@code null} for the other types or a missing row
     */
    record Item(String details, String reason, boolean hasReason, String status) {}

    Item read(UUID tenantId, ApprovalFlowType type, UUID subjectId, String summary) {
        if (type == ApprovalFlowType.LEAVE && subjectId != null) {
            LeaveRequest r =
                    leaveRequests.findByIdAndTenantId(subjectId, tenantId).orElse(null);
            if (r != null) {
                String typeName = leaveTypes
                        .findByTenantIdAndId(tenantId, r.getLeaveTypeId())
                        .map(LeaveType::getName)
                        .orElse("Leave");
                String half = r.isHalfDay()
                        ? " (half day"
                                + (r.getHalfDayPeriod() == null
                                        ? ""
                                        : ", " + half(r.getHalfDayPeriod().name())) + ")"
                        : "";
                return new Item(
                        typeName + ", " + span(r.getFromDate(), r.getToDate()) + half,
                        r.getReason(),
                        true,
                        r.getStatus() == null ? null : r.getStatus().name());
            }
        }
        if (type == ApprovalFlowType.WORK_FROM_HOME && subjectId != null) {
            WfhRequest r = wfhRequests.findByTenantIdAndId(tenantId, subjectId).orElse(null);
            if (r != null) {
                String half = r.isHalfDay()
                        ? " (half day" + (r.getHalfDayPeriod() == null ? "" : ", " + half(r.getHalfDayPeriod())) + ")"
                        : "";
                return new Item(
                        "Work from home, " + span(r.getFromDate(), r.getToDate()) + half,
                        r.getReason(),
                        true,
                        r.getStatus() == null ? null : r.getStatus().name());
            }
        }
        boolean core = type == ApprovalFlowType.LEAVE || type == ApprovalFlowType.WORK_FROM_HOME;
        return new Item(summary == null ? "" : summary, null, core, null);
    }

    static String span(LocalDate from, LocalDate to) {
        if (from == null) {
            return "";
        }
        return to == null || to.equals(from) ? from.toString() : from + " to " + to;
    }

    private static String half(String period) {
        return period.toLowerCase(java.util.Locale.ROOT).startsWith("f") ? "first half" : "second half";
    }
}
