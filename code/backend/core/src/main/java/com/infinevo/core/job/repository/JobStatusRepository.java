package com.infinevo.core.job.repository;

import com.infinevo.core.job.JobState;
import com.infinevo.core.job.entity.JobStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface JobStatusRepository extends JpaRepository<JobStatus, String> {

    Optional<JobStatus> findByJobIdAndTenantId(String jobId, UUID tenantId);

    /**
     * Moves a job from {@code from} to {@code to} in one statement, so two workers that read the
     * same row at the same moment cannot both win. Returns the number of rows changed: 1 when
     * this caller made the transition, 0 when the job was not in {@code from}.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE JobStatus j SET j.status = :to, j.updatedAt = :now " + "WHERE j.jobId = :jobId AND j.status = :from")
    int transition(
            @Param("jobId") String jobId,
            @Param("from") JobState from,
            @Param("to") JobState to,
            @Param("now") Instant now);
}
