package com.infinevo.hrms.timesheet;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads {@code hrms.timesheet_day_entry} (W-42.1). Day entries are reached through their task entry; this exists so
 * that the table has a repository like its siblings, and so that tests can count what a delete leaves behind.
 */
@Transactional(readOnly = true)
public interface TimesheetDayEntryRepository extends JpaRepository<TimesheetDayEntry, UUID> {

    long countByTenantId(UUID tenantId);
}
