-- Migration: V235__surveys_schema.sql
-- Description: W-110 Surveys & Polls schema (survey, survey_question, survey_response, survey_answer)

-- 1. Survey Definition
CREATE TABLE hrms.survey (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    title VARCHAR(150) NOT NULL,
    description TEXT,
    category VARCHAR(32) NOT NULL DEFAULT 'GENERAL',
    is_anonymous BOOLEAN NOT NULL DEFAULT false,
    target_type VARCHAR(32) NOT NULL DEFAULT 'ALL_EMPLOYEES',
    target_id UUID DEFAULT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

ALTER TABLE hrms.survey ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON hrms.survey
    USING (tenant_id = CASE
      WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
      WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
      ELSE current_setting('app.current_tenant_id', true)::uuid
    END);
CREATE INDEX idx_survey_tenant_status ON hrms.survey(tenant_id, status, start_date, end_date);

-- 2. Survey Questions
CREATE TABLE hrms.survey_question (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    survey_id UUID NOT NULL REFERENCES hrms.survey(id) ON DELETE CASCADE,
    question_text VARCHAR(500) NOT NULL,
    question_type VARCHAR(32) NOT NULL,
    options_json JSONB DEFAULT NULL,
    is_required BOOLEAN NOT NULL DEFAULT true,
    sort_order SMALLINT NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

ALTER TABLE hrms.survey_question ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON hrms.survey_question
    USING (tenant_id = CASE
      WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
      WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
      ELSE current_setting('app.current_tenant_id', true)::uuid
    END);
CREATE INDEX idx_survey_question_survey ON hrms.survey_question(tenant_id, survey_id, sort_order);

-- 3. Survey Responses
CREATE TABLE hrms.survey_response (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    survey_id UUID NOT NULL REFERENCES hrms.survey(id) ON DELETE CASCADE,
    employee_id UUID REFERENCES core.employee(id),
    submission_token VARCHAR(64) NOT NULL,
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_survey_response_token UNIQUE (tenant_id, survey_id, submission_token)
);

ALTER TABLE hrms.survey_response ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON hrms.survey_response
    USING (tenant_id = CASE
      WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
      WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
      ELSE current_setting('app.current_tenant_id', true)::uuid
    END);
CREATE INDEX idx_survey_response_survey ON hrms.survey_response(tenant_id, survey_id);

-- 4. Survey Answers
CREATE TABLE hrms.survey_answer (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    response_id UUID NOT NULL REFERENCES hrms.survey_response(id) ON DELETE CASCADE,
    question_id UUID NOT NULL REFERENCES hrms.survey_question(id) ON DELETE CASCADE,
    rating_value INTEGER CHECK (rating_value >= 1 AND rating_value <= 5),
    selected_options JSONB DEFAULT NULL,
    text_value TEXT DEFAULT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE hrms.survey_answer ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON hrms.survey_answer
    USING (tenant_id = CASE
      WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
      WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
      ELSE current_setting('app.current_tenant_id', true)::uuid
    END);
CREATE INDEX idx_survey_answer_question ON hrms.survey_answer(tenant_id, question_id);

