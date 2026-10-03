package com.infinevo.hrms.timesheet.reminder;

import static org.assertj.core.api.Assertions.assertThat;

import com.infinevo.core.notification.ReminderRecipient;
import com.infinevo.shared.tenant.TenantContext;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * W-43.2 §7, {@code TimesheetLateManagerIT}: the two audiences, over the database, with the real late query behind
 * them. The employee's reminder and the manager's list are built from one answer.
 */
class TimesheetLateManagerIT extends TimesheetReminderSupport {

    private static final LocalDate IN_FORCE = LocalDate.of(2026, 1, 1);

    @Autowired
    private TimesheetLateManagerAudienceResolver managerAudience;

    @Autowired
    private TimesheetLateAudienceResolver employeeAudience;

    private List<ReminderRecipient> managers(UUID tenant) {
        TenantContext.set(tenant);
        try {
            return managerAudience.recipients(null, tenant, SLOT);
        } finally {
            TenantContext.clear();
        }
    }

    private List<ReminderRecipient> employees(UUID tenant) {
        TenantContext.set(tenant);
        try {
            return employeeAudience.recipients(null, tenant, SLOT);
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("Two late reports of one manager give one recipient listing both, in name order, with the week")
    void oneListPerManager() throws Exception {
        UUID tenant = newTenant();
        UUID boss = employee(tenant, "Meera", "Boss");
        UUID vikram = lateEmployee(tenant, boss, "Vikram", "Sen");
        UUID asha = lateEmployee(tenant, boss, "Asha", "Rao");
        reportingLine(tenant, vikram, boss, "PRIMARY", IN_FORCE, null);
        reportingLine(tenant, asha, boss, "PRIMARY", IN_FORCE, null);

        List<ReminderRecipient> recipients = managers(tenant);

        assertThat(recipients).hasSize(1);
        assertThat(recipients.get(0).employeeId()).isEqualTo(boss);
        assertThat(recipients.get(0).placeholders())
                .containsEntry("late_employees", "Asha Rao, Vikram Sen")
                .containsEntry("week_start", WEEK.toString());
    }

    @Test
    @DisplayName("Two managers each get their own list, and only their own reports are on it")
    void eachManagerGetsTheirOwnReports() throws Exception {
        UUID tenant = newTenant();
        UUID first = employee(tenant, "Asif", "Boss");
        UUID second = employee(tenant, "Nora", "Boss");
        UUID a = lateEmployee(tenant, first, "Asha", "Rao");
        UUID b = lateEmployee(tenant, first, "Bina", "Das");
        UUID c = lateEmployee(tenant, second, "Chitra", "Iyer");
        reportingLine(tenant, a, first, "PRIMARY", IN_FORCE, null);
        reportingLine(tenant, b, first, "PRIMARY", IN_FORCE, null);
        reportingLine(tenant, c, second, "PRIMARY", IN_FORCE, null);

        List<ReminderRecipient> recipients = managers(tenant);

        assertThat(recipients).extracting(ReminderRecipient::employeeId).containsExactlyInAnyOrder(first, second);
        assertThat(recipients.stream()
                        .filter(r -> r.employeeId().equals(first))
                        .findFirst()
                        .orElseThrow()
                        .placeholders())
                .containsEntry("late_employees", "Asha Rao, Bina Das");
        assertThat(recipients.stream()
                        .filter(r -> r.employeeId().equals(second))
                        .findFirst()
                        .orElseThrow()
                        .placeholders())
                .containsEntry("late_employees", "Chitra Iyer");
    }

    @Test
    @DisplayName(
            "A manager whose line ended before the week is not used, and neither is one whose line starts after it")
    void aLineNotInForceOnTheWeeksLastDayIsNotUsed() throws Exception {
        UUID tenant = newTenant();
        UUID formerBoss = employee(tenant, "Former", "Boss");
        UUID futureBoss = employee(tenant, "Future", "Boss");
        UUID currentBoss = employee(tenant, "Current", "Boss");
        UUID employee = lateEmployee(tenant, currentBoss, "Asha", "Rao");
        // The week runs to Sunday 2026-10-04: the first line ended the day before the week began.
        reportingLine(tenant, employee, formerBoss, "PRIMARY", IN_FORCE, WEEK.minusDays(1));
        reportingLine(tenant, employee, futureBoss, "PRIMARY", WEEK.plusWeeks(1), null);
        reportingLine(tenant, employee, currentBoss, "PRIMARY", WEEK.minusDays(1), null);

        List<ReminderRecipient> recipients = managers(tenant);

        assertThat(recipients).extracting(ReminderRecipient::employeeId).containsExactly(currentBoss);
    }

    @Test
    @DisplayName("A late employee with no manager is left out of the escalation, not given to someone else")
    void noManagerIsLeftOut() throws Exception {
        UUID tenant = newTenant();
        UUID boss = employee(tenant, "Meera", "Boss");
        UUID withBoss = lateEmployee(tenant, boss, "Asha", "Rao");
        lateEmployee(tenant, boss, "Orphan", "Employee");
        reportingLine(tenant, withBoss, boss, "PRIMARY", IN_FORCE, null);

        List<ReminderRecipient> recipients = managers(tenant);

        assertThat(recipients).hasSize(1);
        assertThat(recipients.get(0).placeholders()).containsEntry("late_employees", "Asha Rao");
    }

    @Test
    @DisplayName("A terminated or deleted manager is not a recipient; their reports are left out")
    void aManagerWhoHasLeftIsNotARecipient() throws Exception {
        UUID tenant = newTenant();
        UUID terminated = employee(tenant, "Gone", "Boss");
        UUID deleted = employee(tenant, "Deleted", "Boss");
        UUID a = lateEmployee(tenant, terminated, "Asha", "Rao");
        UUID b = lateEmployee(tenant, deleted, "Bina", "Das");
        reportingLine(tenant, a, terminated, "PRIMARY", IN_FORCE, null);
        reportingLine(tenant, b, deleted, "PRIMARY", IN_FORCE, null);
        setEmploymentStatus(terminated, "TERMINATED");
        softDeleteEmployee(deleted);

        assertThat(managers(tenant)).isEmpty();
    }

    @Test
    @DisplayName("An INDIRECT line makes no escalation: only the primary reporting manager is told")
    void onlyThePrimaryManagerIsTold() throws Exception {
        UUID tenant = newTenant();
        UUID primary = employee(tenant, "Prima", "Boss");
        UUID indirect = employee(tenant, "Indi", "Boss");
        UUID employee = lateEmployee(tenant, primary, "Asha", "Rao");
        reportingLine(tenant, employee, primary, "PRIMARY", IN_FORCE, null);
        reportingLine(tenant, employee, indirect, "INDIRECT", IN_FORCE, null);

        assertThat(managers(tenant)).extracting(ReminderRecipient::employeeId).containsExactly(primary);
    }

    @Test
    @DisplayName("The employee's reminder and the manager's list agree: the same people, from one query")
    void theTwoAudiencesNeverDisagree() throws Exception {
        UUID tenant = newTenant();
        UUID boss = employee(tenant, "Meera", "Boss");
        UUID asha = lateEmployee(tenant, boss, "Asha", "Rao");
        UUID vikram = lateEmployee(tenant, boss, "Vikram", "Sen");
        UUID handedIn = lateEmployee(tenant, boss, "Hari", "Done");
        timesheet(tenant, handedIn, WEEK, "SUBMITTED");
        reportingLine(tenant, asha, boss, "PRIMARY", IN_FORCE, null);
        reportingLine(tenant, vikram, boss, "PRIMARY", IN_FORCE, null);
        reportingLine(tenant, handedIn, boss, "PRIMARY", IN_FORCE, null);

        List<ReminderRecipient> reminded = employees(tenant);

        assertThat(reminded).extracting(ReminderRecipient::employeeId).containsExactly(asha, vikram);
        assertThat(reminded).allSatisfy(r -> assertThat(r.placeholders())
                .containsOnly(java.util.Map.entry("week_start", WEEK.toString())));
        assertThat(managers(tenant).get(0).placeholders().get("late_employees")).isEqualTo("Asha Rao, Vikram Sen");
    }

    @Test
    @DisplayName("resolve() names the same people for the current week, as the older method of an audience does")
    void resolveFollowsTheCurrentWeek() throws Exception {
        UUID tenant = newTenant();
        UUID boss = employee(tenant, "Meera", "Boss");
        UUID asha = lateEmployee(tenant, boss, "Asha", "Rao");
        reportingLine(tenant, asha, boss, "PRIMARY", IN_FORCE, null);

        TenantContext.set(tenant);
        try {
            // The fixture has no timesheet for any week, so Asha is late for last week whenever the test runs.
            assertThat(employeeAudience.resolve(null, tenant)).containsExactly(asha);
            assertThat(managerAudience.resolve(null, tenant)).containsExactly(boss);
        } finally {
            TenantContext.clear();
        }
    }
}
