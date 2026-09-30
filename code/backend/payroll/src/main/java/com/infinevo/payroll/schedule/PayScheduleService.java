package com.infinevo.payroll.schedule;

/**
 * Service for reading and upserting a tenant's pay schedule (W-28 §4).
 */
public interface PayScheduleService {

    /**
     * Returns the current pay schedule for the caller's bound tenant, or a default model
     * with {@code exists: false} if none is configured. Never writes to the database.
     */
    PayScheduleResponse get();

    /**
     * Creates or replaces the pay schedule for the caller's bound tenant.
     *
     * @param request the new schedule values
     * @return the saved schedule
     */
    PayScheduleResponse upsert(PayScheduleRequest request);
}
