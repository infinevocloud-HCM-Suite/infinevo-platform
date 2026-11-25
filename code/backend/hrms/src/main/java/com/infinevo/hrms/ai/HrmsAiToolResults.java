package com.infinevo.hrms.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.infinevo.core.ai.AiToolResults;
import com.infinevo.core.ai.AiUntrustedText;
import com.infinevo.hrms.attendance.ClockSessionResponse;
import com.infinevo.hrms.attendance.RegularizationResponse;
import com.infinevo.hrms.attendance.TodayResponse;
import com.infinevo.hrms.timesheet.TimesheetResponse;
import java.math.BigDecimal;
import java.util.List;

/**
 * The HRMS responses as the AI helper reads them (W-114.6 rules 4 and 7).
 *
 * <p>Each response is copied field by field, so only the fields named here leave the tool: no {@code id}, no
 * {@code employeeId}, no approval instance or decider id. Dates and times are ISO text, enums their names, hours plain
 * decimal text ({@code "7.50"}). A null field is left out. Text written by someone other than the caller - a
 * regularization's {@code decisionComment}, a timesheet line's {@code rejection_reason}, a day's attendance
 * {@code remarks} - is {@link AiUntrustedText#wrap wrapped}; the caller's own {@code reason} and day
 * {@code description} are plain. Every answer passes {@link AiToolResults#withoutIds} as well, as a second guard.
 */
final class HrmsAiToolResults {

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private HrmsAiToolResults() {}

    /** {@code ClockService.today()}: the date, whether a session is open, today's sessions and minutes worked. */
    static JsonNode today(TodayResponse today) {
        ObjectNode out = NODES.objectNode();
        put(out, "date", today.date());
        out.put("clockedIn", today.openSession() != null);
        if (today.openSession() != null) {
            out.set("openSession", session(today.openSession()));
        }
        out.set("sessions", sessions(today.sessions()));
        out.put("workedMinutes", today.workedMinutes());
        return AiToolResults.withoutIds(out);
    }

    /** {@code ClockService.mySessions}: newest first, as the service orders them. */
    static JsonNode sessions(String from, String to, List<ClockSessionResponse> sessions) {
        ObjectNode out = range(from, to);
        out.set("sessions", sessions(sessions));
        out.put("count", sessions == null ? 0 : sessions.size());
        return AiToolResults.withoutIds(out);
    }

    /** {@code RegularizationService.mine}: newest first. */
    static JsonNode regularizations(String from, String to, List<RegularizationResponse> requests) {
        ObjectNode out = range(from, to);
        ArrayNode list = out.putArray("regularizations");
        if (requests != null) {
            requests.forEach(r -> list.add(regularization(r)));
        }
        out.put("count", list.size());
        return AiToolResults.withoutIds(out);
    }

    /** {@code TimesheetService.forWeek}: the week, or {@code timesheet: null} when there is none. */
    static JsonNode week(String weekStart, TimesheetResponse sheet) {
        ObjectNode out = NODES.objectNode();
        if (weekStart != null) {
            out.put("weekStart", weekStart);
        }
        if (sheet == null) {
            out.putNull("timesheet");
            out.put("message", "There is no timesheet for this week.");
        } else {
            out.set("timesheet", timesheet(sheet));
        }
        return AiToolResults.withoutIds(out);
    }

    /** {@code TimesheetService.listMine}: newest week first. */
    static JsonNode timesheets(String from, String to, String status, List<TimesheetResponse> sheets) {
        ObjectNode out = range(from, to);
        if (status != null) {
            out.put("status", status);
        }
        ArrayNode list = out.putArray("timesheets");
        if (sheets != null) {
            sheets.forEach(t -> list.add(timesheet(t)));
        }
        out.put("count", list.size());
        return AiToolResults.withoutIds(out);
    }

    // ---- one row each ----

    static ObjectNode session(ClockSessionResponse s) {
        ObjectNode out = NODES.objectNode();
        put(out, "attendanceDate", s.attendanceDate());
        put(out, "clockInAt", s.clockInAt());
        put(out, "clockOutAt", s.clockOutAt());
        put(out, "workedMinutes", s.workedMinutes());
        put(out, "origin", s.origin());
        put(out, "voidedAt", s.voidedAt());
        put(out, "voidReason", s.voidReason());
        put(out, "dayStatus", s.dayStatus());
        // The day's remarks are core.attendance's, set by HR or the system as often as by the employee: the response
        // does not say who wrote them, so they are treated as another person's text (rule 7).
        untrusted(out, "remarks", s.remarks());
        return out;
    }

    static ObjectNode regularization(RegularizationResponse r) {
        ObjectNode out = NODES.objectNode();
        put(out, "date", r.date());
        put(out, "inAt", r.inAt());
        put(out, "outAt", r.outAt());
        put(out, "reason", r.reason());
        put(out, "status", r.status());
        put(out, "createdAt", r.createdAt());
        put(out, "decidedAt", r.decidedAt());
        put(out, "decidedByName", r.decidedByName());
        untrusted(out, "decisionComment", r.decisionComment());
        return out;
    }

    static ObjectNode timesheet(TimesheetResponse t) {
        ObjectNode out = NODES.objectNode();
        put(out, "week_start_date", t.weekStartDate());
        put(out, "week_end_date", t.weekEndDate());
        put(out, "status", t.status());
        put(out, "submitted_at", t.submittedAt());
        put(out, "updated_at", t.updatedAt());
        BigDecimal weekHours = BigDecimal.ZERO;
        ArrayNode projects = out.putArray("projects");
        if (t.projects() != null) {
            for (TimesheetResponse.ProjectEntryResponse p : t.projects()) {
                ObjectNode project = projects.addObject();
                put(project, "project_name", p.projectName());
                put(project, "status", p.status());
                untrusted(project, "rejection_reason", p.rejectionReason());
                BigDecimal projectHours = BigDecimal.ZERO;
                ArrayNode tasks = project.putArray("tasks");
                if (p.tasks() != null) {
                    for (TimesheetResponse.TaskEntryResponse task : p.tasks()) {
                        ObjectNode taskOut = tasks.addObject();
                        put(taskOut, "task_title", task.taskTitle());
                        ArrayNode days = taskOut.putArray("days");
                        if (task.days() != null) {
                            for (TimesheetResponse.DayEntryResponse day : task.days()) {
                                ObjectNode dayOut = days.addObject();
                                put(dayOut, "date", day.date());
                                put(dayOut, "hours", day.hours());
                                put(dayOut, "description", day.description());
                                if (day.hours() != null) {
                                    projectHours = projectHours.add(day.hours());
                                }
                            }
                        }
                    }
                }
                put(project, "hours", projectHours);
                weekHours = weekHours.add(projectHours);
            }
        }
        put(out, "hours", weekHours);
        return out;
    }

    // ---- writing ----

    private static ObjectNode range(String from, String to) {
        ObjectNode out = NODES.objectNode();
        out.put("from", from);
        out.put("to", to);
        return out;
    }

    private static ArrayNode sessions(List<ClockSessionResponse> sessions) {
        ArrayNode list = NODES.arrayNode();
        if (sessions != null) {
            sessions.forEach(s -> list.add(session(s)));
        }
        return list;
    }

    private static void untrusted(ObjectNode out, String name, String text) {
        ObjectNode wrapped = AiUntrustedText.wrap(text);
        if (wrapped != null) {
            out.set(name, wrapped);
        }
    }

    /**
     * Writes one value: numbers as numbers, except {@link BigDecimal} as plain text; enums by name; dates and times by
     * their ISO {@code toString}; nothing for null.
     */
    private static void put(ObjectNode out, String name, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof BigDecimal decimal) {
            out.put(name, decimal.toPlainString());
        } else if (value instanceof Integer number) {
            out.put(name, number);
        } else if (value instanceof Enum<?> constant) {
            out.put(name, constant.name());
        } else {
            out.put(name, value.toString());
        }
    }
}
