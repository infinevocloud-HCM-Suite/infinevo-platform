package com.infinevo.shared.audit;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Test-only repository for {@link AuditedProbe}. */
public interface AuditedProbeRepository extends JpaRepository<AuditedProbe, UUID> {}
