-- Migration: V138__subscription_status_check.sql
-- Schema: core
-- Purpose: core.subscription.status holds only the four SubscriptionStatus names (W-65.1 review)
--
-- Why. V082 inserted the Infinevo platform tenant's subscription with status 'active', in lower case. The
-- application reads the column as the SubscriptionStatus enum (ACTIVE, PAST_DUE, SUSPENDED, CANCELLED), so that
-- row could not be loaded as an entity: the platform tenant's entitlement lookup and its own subscription read
-- failed ("No enum constant ... SubscriptionStatus.active"), and the tenant list showed it with a status no
-- customer has. V034 declared the column VARCHAR(16) with no CHECK, so nothing refused the value.
--
-- V082 is not edited: it is already applied wherever the branch has been built, and an applied script's checksum
-- must not change. This script corrects the data and closes the gap.

-- 1. Correct what exists. A value that is not a status in any case is left alone for step 2 to refuse, loudly,
--    rather than guessed at.
UPDATE core.subscription
SET status = upper(status),
    updated_at = now(),
    updated_by = 'migration'
WHERE status <> upper(status);

-- 2. Refuse it from now on. core.set_subscription_status (V034) writes through this table, so it is covered too.
ALTER TABLE core.subscription
    ADD CONSTRAINT ck_subscription_status CHECK (status IN ('ACTIVE', 'PAST_DUE', 'SUSPENDED', 'CANCELLED'));
