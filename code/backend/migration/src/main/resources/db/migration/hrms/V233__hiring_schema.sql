-- Migration: V233__hiring_schema.sql
-- Description: W-112 Hiring & Recruitment Management schema (job opening, candidate, job application, interview, feedback, offer)

-- 1. Job Openings
CREATE TABLE IF NOT EXISTS hrms.job_opening (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    opening_code VARCHAR(32) NOT NULL,
    title VARCHAR(120) NOT NULL,
    department_id UUID REFERENCES core.department(id),
    designation_id UUID REFERENCES core.designation(id),
    work_location_id UUID REFERENCES core.work_location(id),
    hiring_manager_id UUID REFERENCES core.employee(id),
    employment_type VARCHAR(32) NOT NULL DEFAULT 'FULL_TIME',
    openings_count INTEGER NOT NULL DEFAULT 1,
    filled_count INTEGER NOT NULL DEFAULT 0,
    min_salary NUMERIC(19,4),
    max_salary NUMERIC(19,4),
    min_experience_years INTEGER DEFAULT 0,
    max_experience_years INTEGER,
    description TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    target_date DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT uk_job_opening_code UNIQUE (tenant_id, opening_code)
);

ALTER TABLE hrms.job_opening ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON hrms.job_opening;
CREATE POLICY tenant_isolation ON hrms.job_opening
    USING (tenant_id = CASE
      WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
      WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
      ELSE current_setting('app.current_tenant_id', true)::uuid
    END);
CREATE INDEX IF NOT EXISTS idx_job_opening_tenant_status ON hrms.job_opening(tenant_id, status);

-- 2. Candidates
CREATE TABLE IF NOT EXISTS hrms.candidate (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    first_name VARCHAR(60) NOT NULL,
    last_name VARCHAR(60) NOT NULL,
    email VARCHAR(120) NOT NULL,
    phone VARCHAR(32),
    current_company VARCHAR(120),
    current_title VARCHAR(120),
    experience_years NUMERIC(4,1),
    notice_period_days INTEGER,
    resume_document_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT uk_candidate_email UNIQUE (tenant_id, email)
);

ALTER TABLE hrms.candidate ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON hrms.candidate;
CREATE POLICY tenant_isolation ON hrms.candidate
    USING (tenant_id = CASE
      WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
      WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
      ELSE current_setting('app.current_tenant_id', true)::uuid
    END);
CREATE INDEX IF NOT EXISTS idx_candidate_tenant_email ON hrms.candidate(tenant_id, email);

-- 3. Job Applications
CREATE TABLE IF NOT EXISTS hrms.job_application (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    job_opening_id UUID NOT NULL REFERENCES hrms.job_opening(id) ON DELETE CASCADE,
    candidate_id UUID NOT NULL REFERENCES hrms.candidate(id) ON DELETE CASCADE,
    stage VARCHAR(32) NOT NULL DEFAULT 'APPLIED',
    rejection_reason VARCHAR(255),
    applied_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT uk_job_application UNIQUE (tenant_id, job_opening_id, candidate_id)
);

ALTER TABLE hrms.job_application ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON hrms.job_application;
CREATE POLICY tenant_isolation ON hrms.job_application
    USING (tenant_id = CASE
      WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
      WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
      ELSE current_setting('app.current_tenant_id', true)::uuid
    END);
CREATE INDEX IF NOT EXISTS idx_job_application_stage ON hrms.job_application(tenant_id, job_opening_id, stage);

-- 4. Interviews
CREATE TABLE IF NOT EXISTS hrms.interview (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    application_id UUID NOT NULL REFERENCES hrms.job_application(id) ON DELETE CASCADE,
    round_name VARCHAR(60) NOT NULL,
    scheduled_at TIMESTAMPTZ NOT NULL,
    duration_minutes INTEGER NOT NULL DEFAULT 45,
    interviewer_id UUID NOT NULL REFERENCES core.employee(id),
    meeting_link VARCHAR(255),
    status VARCHAR(32) NOT NULL DEFAULT 'SCHEDULED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

ALTER TABLE hrms.interview ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON hrms.interview;
CREATE POLICY tenant_isolation ON hrms.interview
    USING (tenant_id = CASE
      WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
      WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
      ELSE current_setting('app.current_tenant_id', true)::uuid
    END);
CREATE INDEX IF NOT EXISTS idx_interview_app_time ON hrms.interview(tenant_id, application_id, scheduled_at);

-- 5. Interview Feedback
CREATE TABLE IF NOT EXISTS hrms.interview_feedback (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    interview_id UUID NOT NULL REFERENCES hrms.interview(id) ON DELETE CASCADE,
    interviewer_id UUID NOT NULL REFERENCES core.employee(id),
    rating INTEGER NOT NULL CHECK (rating >= 1 AND rating <= 5),
    recommendation VARCHAR(32) NOT NULL,
    strengths TEXT,
    weaknesses TEXT,
    comments TEXT,
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_interview_interviewer_feedback UNIQUE (tenant_id, interview_id, interviewer_id)
);

ALTER TABLE hrms.interview_feedback ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON hrms.interview_feedback;
CREATE POLICY tenant_isolation ON hrms.interview_feedback
    USING (tenant_id = CASE
      WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
      WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
      ELSE current_setting('app.current_tenant_id', true)::uuid
    END);

-- 6. Job Offers
CREATE TABLE IF NOT EXISTS hrms.job_offer (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    application_id UUID NOT NULL REFERENCES hrms.job_application(id) ON DELETE CASCADE,
    designation_id UUID NOT NULL REFERENCES core.designation(id),
    department_id UUID NOT NULL REFERENCES core.department(id),
    work_location_id UUID NOT NULL REFERENCES core.work_location(id),
    offered_ctc NUMERIC(19,4) NOT NULL,
    target_join_date DATE NOT NULL,
    offer_valid_until DATE NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    converted_employee_id UUID REFERENCES core.employee(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

ALTER TABLE hrms.job_offer ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS tenant_isolation ON hrms.job_offer;
CREATE POLICY tenant_isolation ON hrms.job_offer
    USING (tenant_id = CASE
      WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
      WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
      ELSE current_setting('app.current_tenant_id', true)::uuid
    END);
CREATE INDEX IF NOT EXISTS idx_job_offer_app ON hrms.job_offer(tenant_id, application_id);

