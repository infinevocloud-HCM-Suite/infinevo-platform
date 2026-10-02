package com.infinevo.hrms.project;

import java.util.List;
import java.util.UUID;

/**
 * Service contract for project management operations (W-41).
 */
public interface ProjectService {

    ProjectResponse create(ProjectRequest request);

    List<ProjectResponse> list(ProjectStatus status, String search, boolean managed);

    List<ProjectResponse> listMine();

    ProjectResponse get(UUID id);

    ProjectResponse update(UUID id, ProjectRequest request);

    ProjectResponse updateStatus(UUID id, ProjectStatus status);

    ProjectResponse updateProgress(UUID id, int progress);

    void delete(UUID id);
}
