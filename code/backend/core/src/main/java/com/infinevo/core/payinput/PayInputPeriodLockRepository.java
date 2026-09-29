package com.infinevo.core.payinput;

import java.time.YearMonth;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and writes {@code core.pay_input_period_lock} (W-19). {@code app_user} holds no
 * {@code UPDATE} or {@code DELETE} on the table ({@code V031}); a lock is written once and never
 * changed.
 */
@Transactional(readOnly = true)
public interface PayInputPeriodLockRepository extends JpaRepository<PayInputPeriodLock, UUID> {

    /**
     * Whether the tenant's period itself is locked — a run lock does not count, even though it
     * carries the run's period too (W-30.1 spec §6). {@link PayInputServiceImpl#record} calls this
     * before every untagged insert so a late input can be redirected; {@code trg_pay_input_period_lock}
     * ({@code V032}, {@code V060}) is what actually stops anything this check missed.
     */
    boolean existsByTenantIdAndPeriodAndRunRefIsNull(UUID tenantId, YearMonth period);

    /** Whether a run is locked (W-30.1) — checked before a tagged insert, in place of the period check. */
    boolean existsByTenantIdAndRunRef(UUID tenantId, UUID runRef);

    /**
     * Locks a period, or does nothing if it is already locked. {@code ON CONFLICT DO NOTHING} rather
     * than a plain insert caught for its unique-index violation: a duplicate key error aborts the
     * underlying Postgres transaction outright, and a caught {@code DataIntegrityViolationException}
     * inside a {@code @Transactional} method cannot make that transaction committable again — the
     * method would return normally onto a connection Postgres has already discarded, and Spring turns
     * that mismatch into {@code UnexpectedRollbackException} at commit. {@code ON CONFLICT} makes a
     * duplicate lock a normal, zero-row outcome instead of an error, so the transaction is never at risk.
     *
     * <p>The conflict target names {@code uk_pay_input_period_lock_tenant_period}'s predicate
     * explicitly ({@code V060}): Postgres only infers a partial unique index from
     * {@code ON CONFLICT} when the statement's own {@code WHERE} matches it exactly, so this insert
     * (which never sets {@code run_ref}, leaving it {@code NULL}) would otherwise fail with
     * "no unique or exclusion constraint matching the ON CONFLICT specification".
     *
     * @return {@code 1} if this call created the lock, {@code 0} if it already existed
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value = "INSERT INTO core.pay_input_period_lock (tenant_id, period, locked_by, created_by, updated_by)"
                    + " VALUES (:tenantId, CAST(:period AS char(7)), :lockedBy, :lockedBy, :lockedBy)"
                    + " ON CONFLICT (tenant_id, period) WHERE run_ref IS NULL DO NOTHING",
            nativeQuery = true)
    @Transactional
    int insertIfAbsent(
            @Param("tenantId") UUID tenantId, @Param("period") String period, @Param("lockedBy") String lockedBy);

    /**
     * Locks a run, or does nothing if it is already locked (W-30.1) — {@code period} is stored
     * alongside it for reporting (spec §4). Targets {@code uk_pay_input_period_lock_tenant_run}'s
     * predicate explicitly, the same reason {@link #insertIfAbsent} targets the period index's.
     *
     * @return {@code 1} if this call created the lock, {@code 0} if it already existed
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value =
                    "INSERT INTO core.pay_input_period_lock (tenant_id, period, run_ref, locked_by, created_by, updated_by)"
                            + " VALUES (:tenantId, CAST(:period AS char(7)), :runRef, :lockedBy, :lockedBy, :lockedBy)"
                            + " ON CONFLICT (tenant_id, run_ref) WHERE run_ref IS NOT NULL DO NOTHING",
            nativeQuery = true)
    @Transactional
    int insertRunLockIfAbsent(
            @Param("tenantId") UUID tenantId,
            @Param("period") String period,
            @Param("runRef") UUID runRef,
            @Param("lockedBy") String lockedBy);
}
