-- Migration: V120__overtime_request_states.sql
-- Description: W-40.5 core.overtime_request — widen status CHECK to include PENDING and REJECTED

ALTER TABLE core.overtime_request DROP CONSTRAINT overtime_request_status_check;
ALTER TABLE core.overtime_request ADD CONSTRAINT overtime_request_status_check
    CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'));
