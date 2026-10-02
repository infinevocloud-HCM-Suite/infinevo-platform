package com.infinevo.hrms.project;

import java.util.List;
import java.util.UUID;

/**
 * Service contract for project task operations (W-41).
 */
public interface TaskService {

    TaskResponse create(UUID projectId, TaskRequest request);

    List<TaskResponse> listByProject(UUID projectId, TaskStatus status);

    List<TaskResponse> listMine();

    TaskResponse get(UUID id);

    TaskResponse update(UUID id, TaskRequest request);

    TaskResponse updateStatus(UUID id, TaskStatus status);

    void delete(UUID id);
}
