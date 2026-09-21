-- Migration: V003__job_status_and_shedlock.sql
-- Schema: core
-- Purpose: Job tracking and distributed scheduler locking (W-52)

-- 1. Job Status Table
CREATE TABLE core.job_status (
    job_id VARCHAR(64) PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    queue_name VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    progress_percentage INT DEFAULT 0 CHECK (progress_percentage BETWEEN 0 AND 100),
    result_payload TEXT,
    error_message TEXT,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP NOT NULL
);

-- RLS and Indexes for core.job_status
ALTER TABLE core.job_status ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON core.job_status
    USING (
        CASE
            WHEN current_setting('app.current_tenant_id', true) IS NULL THEN false
            WHEN current_setting('app.current_tenant_id', true) = '' THEN false
            ELSE tenant_id = current_setting('app.current_tenant_id', true)::uuid
        END
    )
    WITH CHECK (
        CASE
            WHEN current_setting('app.current_tenant_id', true) IS NULL THEN false
            WHEN current_setting('app.current_tenant_id', true) = '' THEN false
            ELSE tenant_id = current_setting('app.current_tenant_id', true)::uuid
        END
    );

CREATE INDEX idx_job_status_tenant_queue ON core.job_status (tenant_id, queue_name);
CREATE INDEX idx_job_status_created ON core.job_status (created_at);

-- 2. ShedLock Table
-- ShedLock table is shared across worker instances for cluster synchronization
CREATE TABLE core.shedlock (
    name VARCHAR(64) NOT NULL PRIMARY KEY,
    lock_until TIMESTAMPTZ NOT NULL,
    locked_at TIMESTAMPTZ NOT NULL,
    locked_by VARCHAR(255) NOT NULL
);

GRANT SELECT, INSERT, UPDATE, DELETE ON core.job_status TO app_user;
GRANT SELECT, INSERT, UPDATE, DELETE ON core.shedlock TO app_user;
