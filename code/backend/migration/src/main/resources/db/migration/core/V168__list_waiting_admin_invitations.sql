-- Migration: V168__list_waiting_admin_invitations.sql
-- Schema: core
-- Purpose: W-73.2 the platform dashboard's "Needs attention" table - every tenant whose administrator
--          has not yet taken up their invitation, with the invitation's state and expiry.
--
-- core.list_tenants() (V159) reports only ACCEPTED, a live PENDING, or NONE: an expired invitation
-- reads NONE and no expiry is returned. The invitation rows sit behind row-level security on
-- core.user_invitation (V117), so the platform tenant cannot read another tenant's rows; this function
-- is SECURITY DEFINER, as V082 and V159 made the tenant list.
--
-- One row per tenant that has no accepted tenant-admin invitation and whose latest tenant-admin
-- invitation - ignoring ones a resend superseded - is still waiting:
--   PENDING   status PENDING and not yet expired
--   EXPIRED   status EXPIRED, or status PENDING past its expires_at
-- A tenant whose latest such invitation was revoked or declined is not listed: nobody is waiting.
-- No table is created, so no tenant_id column or RLS policy is added.
--
-- Platform only: the function returns nothing unless the transaction is bound to the Infinevo platform
-- tenant (the fixed id V082 seeds), so a caller bound to a customer tenant reads no other tenant's
-- invitations even if the application-side check were bypassed.

CREATE OR REPLACE FUNCTION core.list_waiting_admin_invitations()
RETURNS TABLE (
    tenant_id uuid,
    invitation_id uuid,
    email varchar(255),
    invitation_status text,
    expires_at timestamptz
)
LANGUAGE sql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
    SELECT
        t.tenant_id,
        ai.id,
        ai.email,
        CASE WHEN ai.status = 'PENDING' AND ai.expires_at > now() THEN 'PENDING' ELSE 'EXPIRED' END,
        ai.expires_at
    FROM core.tenant t
    JOIN LATERAL (
        SELECT ui.id, ui.email, ui.status, ui.expires_at
        FROM core.user_invitation ui
        JOIN core.user_invitation_role uir
          ON uir.tenant_id = ui.tenant_id
         AND uir.invitation_id = ui.id
        JOIN core.role r
          ON r.tenant_id = uir.tenant_id
         AND r.id = uir.role_id
         AND r.code = 'tenant-admin'
        WHERE ui.tenant_id = t.tenant_id
          AND ui.superseded_by_id IS NULL
        ORDER BY ui.created_at DESC
        LIMIT 1
    ) ai ON ai.status IN ('PENDING', 'EXPIRED')
    WHERE current_setting('app.current_tenant_id', true) = '00000000-0000-0000-0000-000000000001'
      AND NOT EXISTS (
        SELECT 1
        FROM core.user_invitation acc
        JOIN core.user_invitation_role accr
          ON accr.tenant_id = acc.tenant_id
         AND accr.invitation_id = acc.id
        JOIN core.role accrole
          ON accrole.tenant_id = accr.tenant_id
         AND accrole.id = accr.role_id
         AND accrole.code = 'tenant-admin'
        WHERE acc.tenant_id = t.tenant_id
          AND acc.status = 'ACCEPTED'
    )
    ORDER BY ai.expires_at, t.tenant_id;
$$;

REVOKE EXECUTE ON FUNCTION core.list_waiting_admin_invitations() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION core.list_waiting_admin_invitations() TO app_user;
