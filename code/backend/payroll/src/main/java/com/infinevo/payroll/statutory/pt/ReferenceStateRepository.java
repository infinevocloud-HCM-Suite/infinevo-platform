package com.infinevo.payroll.statutory.pt;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Read-only repository for {@link ReferenceState}.
 */
public interface ReferenceStateRepository extends JpaRepository<ReferenceState, String> {
    Optional<ReferenceState> findByCode(String code);
}
