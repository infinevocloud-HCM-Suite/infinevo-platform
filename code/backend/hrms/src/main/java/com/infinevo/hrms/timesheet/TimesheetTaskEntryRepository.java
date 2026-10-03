package com.infinevo.hrms.timesheet;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** Reads {@code hrms.timesheet_task_entry} (W-42.1). Every finder takes {@code tenantId} (DEBT-022). */
@Transactional(readOnly = true)
public interface TimesheetTaskEntryRepository extends JpaRepository<TimesheetTaskEntry, UUID> {

    /**
     * Whether any timesheet that is not cancelled has a line for this task. W-41 refuses to delete a task for as
     * long as this holds.
     */
    @Query(
            """
        SELECT COUNT(te) > 0 FROM TimesheetTaskEntry te
        WHERE te.tenantId = :tenantId
          AND te.taskId = :taskId
          AND te.projectEntry.timesheet.status <> com.infinevo.hrms.timesheet.TimesheetStatus.CANCELLED
    """)
    boolean existsLiveForTask(@Param("tenantId") UUID tenantId, @Param("taskId") UUID taskId);
}
