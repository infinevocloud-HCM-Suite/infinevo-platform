-- Migration: V199__subscription_period_end.sql
-- Schema: core
-- Purpose: W-86 overdue tracking: period end date setter overload, nightly past-due sweep list function,
--          and grant set_subscription_status to worker_user.

-- 1. Five-argument setter for platform staff, preserving the existing V188 4-arg function for rolling deploy.
CREATE OR REPLACE FUNCTION core.set_subscription_plan(
    p_tenant_id uuid,
    p_plan text,
    p_support_plan text,
    p_employee_limit integer,
    p_current_period_end date
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    UPDATE core.subscription
    SET plan = p_plan,
        support_plan = p_support_plan,
        employee_limit = p_employee_limit,
        current_period_end = p_current_period_end,
        updated_at = now()
    WHERE tenant_id = p_tenant_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'Subscription not found for tenant %', p_tenant_id;
    END IF;
END;
$$;

REVOKE EXECUTE ON FUNCTION core.set_subscription_plan(uuid, text, text, integer, date) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION core.set_subscription_plan(uuid, text, text, integer, date) TO app_user;

-- 2. Nightly sweep query: finds active tenants whose period end is strictly before p_as_of.
--    SECURITY DEFINER runs with migration_user privileges to bypass RLS for worker sweep.
CREATE OR REPLACE FUNCTION core.list_overdue_subscriptions(p_as_of date)
RETURNS TABLE (tenant_id uuid)
LANGUAGE sql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
    SELECT s.tenant_id
    FROM core.subscription s
    WHERE s.status = 'ACTIVE'
      AND s.current_period_end IS NOT NULL
      AND s.current_period_end < p_as_of;
$$;

REVOKE EXECUTE ON FUNCTION core.list_overdue_subscriptions(date) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION core.list_overdue_subscriptions(date) TO worker_user;
GRANT EXECUTE ON FUNCTION core.list_overdue_subscriptions(date) TO app_user;

-- 3. Worker user needs to update subscription status during sweep.
GRANT EXECUTE ON FUNCTION core.set_subscription_status(uuid, text) TO worker_user;

