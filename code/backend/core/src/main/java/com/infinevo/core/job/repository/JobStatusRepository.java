package com.infinevo.core.job.repository;

import com.infinevo.core.job.entity.JobStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JobStatusRepository extends JpaRepository<JobStatus, String> {

    Optional<JobStatus> findByJobIdAndTenantId(String jobId, UUID tenantId);
}
