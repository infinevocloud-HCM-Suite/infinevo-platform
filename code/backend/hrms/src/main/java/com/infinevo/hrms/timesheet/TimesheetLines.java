package com.infinevo.hrms.timesheet;

import com.infinevo.hrms.timesheet.TimesheetRequest.DayLine;
import com.infinevo.hrms.timesheet.TimesheetRequest.ProjectLine;
import com.infinevo.hrms.timesheet.TimesheetRequest.TaskLine;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Small helpers shared by the services that write timesheet lines (W-42.1 save, W-42.3 resubmit): turning request
 * lines into entities, the audit actor, and the hours a week already holds.
 */
final class TimesheetLines {

    private TimesheetLines() {}

    /** Adds the tasks and days of a project line to a project entry. Hours are held at scale 2, as the column is. */
    static void addTasks(TimesheetProjectEntry entry, ProjectLine line, String actor) {
        for (TaskLine task : line.tasks()) {
            TimesheetTaskEntry taskEntry = entry.addTask(task.taskId(), actor);
            for (DayLine day : task.days()) {
                taskEntry.addDay(day.date(), day.hours().setScale(2), day.description(), actor);
            }
        }
    }

    /** Hours per date held on the given entries, across all their tasks. */
    static Map<LocalDate, BigDecimal> hoursByDate(Iterable<TimesheetProjectEntry> entries) {
        Map<LocalDate, BigDecimal> totals = new TreeMap<>();
        for (TimesheetProjectEntry entry : entries) {
            for (TimesheetTaskEntry task : entry.getTasks()) {
                for (TimesheetDayEntry day : task.getDays()) {
                    totals.merge(day.getWorkDate(), day.getHours(), BigDecimal::add);
                }
            }
        }
        return totals;
    }

    /** The audit actor: the authenticated caller's name, or {@code system}. */
    static String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null
                || !auth.isAuthenticated()
                || auth.getName() == null
                || auth.getName().isBlank()) {
            return TimesheetRow.ACTOR_SYSTEM;
        }
        String name = auth.getName();
        return name.length() > 100 ? name.substring(0, 100) : name;
    }
}
