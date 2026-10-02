package com.infinevo.hrms.timesheet;

import com.infinevo.hrms.project.TimesheetUsage;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Answers {@link TimesheetUsage} from the timesheet tables (W-42.1). */
@Component
class TimesheetUsageImpl implements TimesheetUsage {

    private final TimesheetProjectEntryRepository projectEntries;
    private final TimesheetTaskEntryRepository taskEntries;

    TimesheetUsageImpl(TimesheetProjectEntryRepository projectEntries, TimesheetTaskEntryRepository taskEntries) {
        this.projectEntries = Objects.requireNonNull(projectEntries, "projectEntries must not be null");
        this.taskEntries = Objects.requireNonNull(taskEntries, "taskEntries must not be null");
    }

    @Override
    public boolean projectInUse(UUID tenantId, UUID projectId) {
        return projectEntries.existsLiveForProject(tenantId, projectId);
    }

    @Override
    public boolean taskInUse(UUID tenantId, UUID taskId) {
        return taskEntries.existsLiveForTask(tenantId, taskId);
    }
}
