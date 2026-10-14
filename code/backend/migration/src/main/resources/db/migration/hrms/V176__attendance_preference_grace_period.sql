-- Migration: V176__attendance_preference_grace_period.sql
-- Schema: hrms
-- Purpose: Add grace_period_minutes column to hrms.attendance_preference for attendance grace period configuration.

ALTER TABLE hrms.attendance_preference
    ADD COLUMN grace_period_minutes INTEGER DEFAULT 0;

ALTER TABLE hrms.attendance_preference
    ADD CONSTRAINT attendance_preference_grace_check
    CHECK (grace_period_minutes IS NULL OR (grace_period_minutes >= 0 AND grace_period_minutes <= 120));
