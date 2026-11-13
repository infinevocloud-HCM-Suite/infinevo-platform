-- Migration: V191__pay_input_note.sql
-- Description: D-140 - note on core.pay_input. The human reason for a hand-entered row (the Loss
-- of pay screen's "Reason"), or a reversal's reason. Display only: it takes no part in idempotency,
-- which stays on (tenant_id, source_module, source_ref) through uk_pay_input_tenant_source (V031).
-- Before this column the screen sent its free-text reason as source_ref, so two entries with the
-- same reason collided on that index and a reason over 64 characters overflowed the column.
-- Expand only: one nullable column, no row touched, no grant change (app_user's table-level INSERT
-- covers the new column; UPDATE stays revoked, V031).

ALTER TABLE core.pay_input ADD COLUMN note VARCHAR(200);

COMMENT ON COLUMN core.pay_input.note IS
    'Human reason for a hand-entered row or a reversal (D-140). Display only; takes no part in idempotency.';
