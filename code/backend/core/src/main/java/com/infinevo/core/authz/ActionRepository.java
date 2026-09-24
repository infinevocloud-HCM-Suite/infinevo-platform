package com.infinevo.core.authz;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Reads {@code reference.action} (W-11.1). Reads only — {@link Action} is immutable and
 * {@code app_user} holds no write grant on {@code reference}.
 *
 * <p>No tenant in any signature, and correctly: the catalogue is the same for every tenant
 * ({@code D-08}). {@code findAllById} is what checks a role's action codes exist.
 */
public interface ActionRepository extends JpaRepository<Action, String> {

    /** The whole catalogue, ordered by code so the list is stable. */
    List<Action> findAllByOrderByCodeAsc();
}
