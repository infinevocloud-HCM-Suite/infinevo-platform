package com.infinevo.hrms.project;

import java.util.List;
import java.util.UUID;

/**
 * Service contract for project team assignment operations (W-41).
 */
public interface AssignmentService {

    List<AssignmentResponse> listByProject(UUID projectId);

    AssignmentResponse assign(UUID projectId, AssignmentRequest request);

    void remove(UUID projectId, UUID employeeId);
}
