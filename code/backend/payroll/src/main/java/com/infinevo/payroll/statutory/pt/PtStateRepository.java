package com.infinevo.payroll.statutory.pt;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Read-only repository for {@link PtState}.
 */
public interface PtStateRepository extends JpaRepository<PtState, String> {
    Optional<PtState> findByStateCode(String stateCode);
}
