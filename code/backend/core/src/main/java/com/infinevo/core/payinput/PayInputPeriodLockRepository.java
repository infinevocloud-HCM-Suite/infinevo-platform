package com.infinevo.core.payinput;

import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and writes {@code core.pay_input_period_lock} (W-19). Since D-144 ({@code V220}) a period
 * lock carries a stage, and {@code app_user} may make exactly two changes, both here as single
 * statements: {@link #closeIfLocked} ({@code LOCKED -> CLOSED}) and {@link #deleteIfLocked}. The
 * restrictive policies in {@code V220} refuse anything else.
 */
@Transactional(readOnly = true)
public interface PayInputPeriodLockRepository extends JpaRepository<PayInputPeriodLock, UUID> {

    /**
     * Whether the tenant's period itself has a lock of any stage — a run lock does not count, even
     * though it carries the run's period too (W-30.1 spec §6). Posting no longer reads this (D-144:
     * {@link #findPeriodStage}); a LOCKED month still takes entries.
     */
    boolean existsByTenantIdAndPeriodAndRunRefIsNull(UUID tenantId, YearMonth period);

    /**
     * The stage of the tenant's period lock for {@code period}, or empty when the month has none -
     * a run lock never counts (W-30.1). {@link PayInputServiceImpl}'s one posting rule reads this
     * (D-144); {@code trg_pay_input_period_lock} ({@code V220}) is what refuses a CLOSED month anyway.
     */
    @Query("select l.stage from PayInputPeriodLock l"
            + " where l.tenantId = :tenantId and l.period = :period and l.runRef is null")
    Optional<PayInputLockStage> findPeriodStage(@Param("tenantId") UUID tenantId, @Param("period") YearMonth period);

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
     * <p>{@code stage} is {@code LOCKED} or {@code CLOSED} (D-144). An existing row is left as it
     * is whatever its stage - raising it is {@link #closeIfLocked}, and nothing lowers it.
     *
     * @return {@code 1} if this call created the lock, {@code 0} if it already existed
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value =
                    "INSERT INTO core.pay_input_period_lock (tenant_id, period, stage, locked_by, created_by, updated_by)"
                            + " VALUES (:tenantId, CAST(:period AS char(7)), :stage, :lockedBy, :lockedBy, :lockedBy)"
                            + " ON CONFLICT (tenant_id, period) WHERE run_ref IS NULL DO NOTHING",
            nativeQuery = true)
    @Transactional
    int insertIfAbsent(
            @Param("tenantId") UUID tenantId,
            @Param("period") String period,
            @Param("stage") String stage,
            @Param("lockedBy") String lockedBy);

    /**
     * Raises the tenant's period lock from LOCKED to CLOSED - the run was approved (D-144). A
     * CLOSED row, or no row, is a zero-row outcome. Allowed by {@code V220}'s column grant and its
     * {@code close_period_lock_only} policy, which refuses any other UPDATE.
     *
     * @return {@code 1} if the lock was raised, {@code 0} otherwise
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value = "UPDATE core.pay_input_period_lock SET stage = 'CLOSED', updated_at = CURRENT_TIMESTAMP,"
                    + " updated_by = :actor WHERE tenant_id = :tenantId AND period = CAST(:period AS char(7))"
                    + " AND run_ref IS NULL AND stage = 'LOCKED'",
            nativeQuery = true)
    @Transactional
    int closeIfLocked(@Param("tenantId") UUID tenantId, @Param("period") String period, @Param("actor") String actor);

    /**
     * Removes the tenant's LOCKED period lock - the run was cancelled (D-144, D-138). A CLOSED row is
     * never removed: the statement does not match it, and {@code V220}'s
     * {@code delete_locked_period_lock_only} policy would hide it from a DELETE anyway.
     *
     * @return {@code 1} if a lock was removed, {@code 0} otherwise
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value = "DELETE FROM core.pay_input_period_lock WHERE tenant_id = :tenantId"
                    + " AND period = CAST(:period AS char(7)) AND run_ref IS NULL AND stage = 'LOCKED'",
            nativeQuery = true)
    @Transactional
    int deleteIfLocked(@Param("tenantId") UUID tenantId, @Param("period") String period);

    /**
     * Removes the tenant's period lock whatever its stage - the month's approved run was cancelled
     * (D-151). Allowed by {@code payroll/V223}'s {@code delete_period_lock_only} policy, which still hides
     * every run lock from a DELETE.
     *
     * @return {@code 1} if a lock was removed, {@code 0} otherwise
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value = "DELETE FROM core.pay_input_period_lock WHERE tenant_id = :tenantId"
                    + " AND period = CAST(:period AS char(7)) AND run_ref IS NULL",
            nativeQuery = true)
    @Transactional
    int deletePeriodLock(@Param("tenantId") UUID tenantId, @Param("period") String period);

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
