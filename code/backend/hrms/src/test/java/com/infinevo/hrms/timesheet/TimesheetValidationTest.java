package com.infinevo.hrms.timesheet;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.hrms.timesheet.TimesheetRequest.DayLine;
import com.infinevo.hrms.timesheet.TimesheetRequest.ProjectLine;
import com.infinevo.hrms.timesheet.TimesheetRequest.TaskLine;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * W-42.1 §4, "Validation": every row of the table, one case each, over the pure rules (no database). What needs the
 * database (a project the caller is assigned to, a task of that project) is {@code TimesheetCrudIT}'s.
 */
class TimesheetValidationTest {

    /** A Monday. */
    private static final LocalDate WEEK = LocalDate.of(2026, 10, 5);

    private static final UUID PROJECT = UUID.randomUUID();
    private static final UUID TASK = UUID.randomUUID();

    private static DayLine day(LocalDate date, String hours) {
        return new DayLine(date, hours == null ? null : new BigDecimal(hours), null);
    }

    private static TimesheetRequest of(LocalDate week, DayLine... days) {
        return new TimesheetRequest(
                week, List.of(new ProjectLine(PROJECT, List.of(new TaskLine(TASK, List.of(days))))));
    }

    private static Map<String, String> errors(TimesheetRequest request) {
        return TimesheetRules.validate(request);
    }

    @Test
    @DisplayName("A sound request has no errors")
    void soundRequest() {
        assertThat(errors(of(WEEK, day(WEEK, "8"), day(WEEK.plusDays(6), "0.25"))))
                .isEmpty();
    }

    @Test
    @DisplayName("The week must start on a Monday: a Tuesday is refused, and so is no date at all")
    void weekStartIsAMonday() {
        assertThat(errors(of(WEEK.plusDays(1), day(WEEK.plusDays(1), "8"))))
                .containsKey("week_start_date")
                .extractingByKey("week_start_date")
                .asString()
                .contains("Monday");
        assertThat(errors(of(null, day(WEEK, "8")))).containsKey("week_start_date");
    }

    @Test
    @DisplayName("Every date must fall inside the week, Monday to Sunday")
    void dateInsideTheWeek() {
        assertThat(errors(of(WEEK, day(WEEK.minusDays(1), "8")))).containsKey("projects[0].tasks[0].days[0].date");
        assertThat(errors(of(WEEK, day(WEEK.plusDays(7), "8")))).containsKey("projects[0].tasks[0].days[0].date");
        assertThat(errors(of(WEEK, day(WEEK, "8"), day(WEEK.plusDays(6), "8")))).isEmpty();
        assertThat(errors(of(WEEK, day(null, "8")))).containsKey("projects[0].tasks[0].days[0].date");
    }

    @Test
    @DisplayName("A project once per timesheet, a task once per project, a date once per task")
    void noDuplicates() {
        TimesheetRequest twoProjects = new TimesheetRequest(
                WEEK,
                List.of(
                        new ProjectLine(PROJECT, List.of(new TaskLine(TASK, List.of(day(WEEK, "1"))))),
                        new ProjectLine(PROJECT, List.of(new TaskLine(UUID.randomUUID(), List.of(day(WEEK, "1")))))));
        assertThat(errors(twoProjects)).containsKey("projects[1].project_id");

        TimesheetRequest twoTasks = new TimesheetRequest(
                WEEK,
                List.of(new ProjectLine(
                        PROJECT,
                        List.of(
                                new TaskLine(TASK, List.of(day(WEEK, "1"))),
                                new TaskLine(TASK, List.of(day(WEEK.plusDays(1), "1")))))));
        assertThat(errors(twoTasks)).containsKey("projects[0].tasks[1].task_id");

        assertThat(errors(of(WEEK, day(WEEK, "1"), day(WEEK, "2")))).containsKey("projects[0].tasks[0].days[1].date");
    }

    @Test
    @DisplayName("Hours are greater than 0 and at most 24, with two decimals: 0, negative, 24.01 and 7.255 are refused")
    void hoursBounds() {
        String key = "projects[0].tasks[0].days[0].hours";
        assertThat(errors(of(WEEK, day(WEEK, "0")))).containsKey(key);
        assertThat(errors(of(WEEK, day(WEEK, "-1")))).containsKey(key);
        assertThat(errors(of(WEEK, day(WEEK, "24.01")))).containsKey(key);
        assertThat(errors(of(WEEK, day(WEEK, "7.255")))).containsKey(key);
        assertThat(errors(of(WEEK, day(WEEK, null)))).containsKey(key);

        assertThat(errors(of(WEEK, day(WEEK, "24")))).isEmpty();
        assertThat(errors(of(WEEK, day(WEEK, "0.01")))).isEmpty();
        assertThat(errors(of(WEEK, day(WEEK, "7.50")))).isEmpty();
        assertThat(errors(of(WEEK, day(WEEK, "7.500"))))
                .as("trailing zeros are not decimals")
                .isEmpty();
    }

    @Test
    @DisplayName("A day's total across all tasks is at most 24: two tasks of 12.5 hours on one day are refused")
    void dayTotalAcrossTasks() {
        TimesheetRequest request = new TimesheetRequest(
                WEEK,
                List.of(new ProjectLine(
                        PROJECT,
                        List.of(
                                new TaskLine(TASK, List.of(day(WEEK, "12.5"))),
                                new TaskLine(UUID.randomUUID(), List.of(day(WEEK, "12.5")))))));

        assertThat(errors(request)).containsKey("days[" + WEEK + "]");
        assertThat(errors(request).get("days[" + WEEK + "]")).contains("25.0");
    }

    @Test
    @DisplayName("The 24 hours a day is counted across projects too, and 24 exactly is allowed")
    void dayTotalAcrossProjects() {
        UUID otherProject = UUID.randomUUID();
        TimesheetRequest over = new TimesheetRequest(
                WEEK,
                List.of(
                        new ProjectLine(PROJECT, List.of(new TaskLine(TASK, List.of(day(WEEK, "20"))))),
                        new ProjectLine(
                                otherProject, List.of(new TaskLine(UUID.randomUUID(), List.of(day(WEEK, "4.01")))))));
        assertThat(errors(over)).containsKey("days[" + WEEK + "]");

        TimesheetRequest exactly = new TimesheetRequest(
                WEEK,
                List.of(
                        new ProjectLine(PROJECT, List.of(new TaskLine(TASK, List.of(day(WEEK, "20"))))),
                        new ProjectLine(
                                otherProject, List.of(new TaskLine(UUID.randomUUID(), List.of(day(WEEK, "4")))))));
        assertThat(errors(exactly)).isEmpty();
    }

    @Test
    @DisplayName("At least one project with one day entry: nothing, an empty project, and an empty task are refused")
    void atLeastOneDayEntry() {
        assertThat(errors(new TimesheetRequest(WEEK, List.of()))).containsKey("projects");
        assertThat(errors(new TimesheetRequest(WEEK, null))).containsKey("projects");
        assertThat(errors(new TimesheetRequest(WEEK, List.of(new ProjectLine(PROJECT, List.of())))))
                .containsKey("projects[0].tasks");
        assertThat(errors(new TimesheetRequest(WEEK, List.of(new ProjectLine(PROJECT, null)))))
                .containsKey("projects[0].tasks");
        assertThat(errors(new TimesheetRequest(
                        WEEK, List.of(new ProjectLine(PROJECT, List.of(new TaskLine(TASK, List.of())))))))
                .containsKey("projects[0].tasks[0].days");
        assertThat(errors(null)).containsKey("request");
    }

    @Test
    @DisplayName("A description is at most 500 characters")
    void descriptionLength() {
        String longest = "x".repeat(500);
        String tooLong = "x".repeat(501);
        assertThat(errors(of(WEEK, new DayLine(WEEK, BigDecimal.ONE, longest)))).isEmpty();
        assertThat(errors(of(WEEK, new DayLine(WEEK, BigDecimal.ONE, tooLong))))
                .containsKey("projects[0].tasks[0].days[0].description");
    }

    @Test
    @DisplayName("A missing project id or task id is named")
    void idsAreRequired() {
        TimesheetRequest noProjectId = new TimesheetRequest(
                WEEK, List.of(new ProjectLine(null, List.of(new TaskLine(TASK, List.of(day(WEEK, "1")))))));
        assertThat(errors(noProjectId)).containsKey("projects[0].project_id");

        TimesheetRequest noTaskId = new TimesheetRequest(
                WEEK, List.of(new ProjectLine(PROJECT, List.of(new TaskLine(null, List.of(day(WEEK, "1")))))));
        assertThat(errors(noTaskId)).containsKey("projects[0].tasks[0].task_id");
    }

    @Test
    @DisplayName("Every error in the request is reported at once, not only the first")
    void allErrorsTogether() {
        Map<String, String> found = errors(of(WEEK.plusDays(2), day(WEEK, "25"), day(WEEK, "1")));

        assertThat(found).containsKeys("week_start_date", "projects[0].tasks[0].days[0].hours");
        assertThat(found).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("mondayOf finds the Monday of any day in the week, and a Monday is its own")
    void mondayOf() {
        for (int i = 0; i < 7; i++) {
            assertThat(TimesheetRules.mondayOf(WEEK.plusDays(i))).isEqualTo(WEEK);
        }
        assertThat(TimesheetRules.mondayOf(WEEK.plusDays(7))).isEqualTo(WEEK.plusDays(7));
    }
}
