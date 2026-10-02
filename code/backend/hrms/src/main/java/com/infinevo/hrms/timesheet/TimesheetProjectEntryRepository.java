package com.infinevo.hrms.timesheet;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** Reads {@code hrms.timesheet_project_entry} (W-42.1). Every finder takes {@code tenantId} (DEBT-022). */
@Transactional(readOnly = true)
public interface TimesheetProjectEntryRepository extends JpaRepository<TimesheetProjectEntry, UUID> {

    /** One project line of any employee in the tenant (W-42.3): for the approval outcome and the approver's read. */
    Optional<TimesheetProjectEntry> findByTenantIdAndId(UUID tenantId, UUID id);

    /**
     * Whether any timesheet that is not cancelled has a line for this project. W-41 refuses to delete a project
     * for as long as this holds.
     */
    @Query(
            """
        SELECT COUNT(pe) > 0 FROM TimesheetProjectEntry pe
        WHERE pe.tenantId = :tenantId
          AND pe.projectId = :projectId
          AND pe.timesheet.status <> com.infinevo.hrms.timesheet.TimesheetStatus.CANCELLED
    """)
    boolean existsLiveForProject(@Param("tenantId") UUID tenantId, @Param("projectId") UUID projectId);
}
