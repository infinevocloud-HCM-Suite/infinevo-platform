-- Migration: V175__default_reimbursement_components.sql
-- Schema: payroll
-- Purpose: E-09 / BUG-D5-024 — seed standard reimbursement components (INTERNET, TRAVEL, MEDICAL, FOOD, FUEL)
-- for all non-platform tenants so employees have claimable components in the New Claim drawer (/me).
-- Also updates reference.country_template 'IN' salary_components payload to include reimbursements.

-- 1. Function to seed default reimbursements for a tenant
CREATE OR REPLACE FUNCTION payroll.seed_default_reimbursements(p_tenant_id UUID)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    IF p_tenant_id = '00000000-0000-0000-0000-000000000001'::uuid THEN
        RETURN;
    END IF;

    INSERT INTO payroll.reimbursement (
        tenant_id, code, name, display_name, reimbursement_type, calculation_type,
        default_value, max_limit, is_included_in_ctc, is_included_in_salary_structure,
        is_fbp_component, is_opt_in, is_active, is_deleted, created_by, updated_by
    )
    VALUES
        (p_tenant_id, 'INTERNET', 'Internet', 'Internet Reimbursement', 'GENERAL', 'FLAT', 0, 2000.0000, false, false, false, false, true, false, 'template', 'template'),
        (p_tenant_id, 'TRAVEL', 'Travel', 'Travel Reimbursement', 'TRAVEL', 'FLAT', 0, 5000.0000, false, false, false, false, true, false, 'template', 'template'),
        (p_tenant_id, 'MEDICAL', 'Medical', 'Medical Reimbursement', 'MEDICAL', 'FLAT', 0, 15000.0000, false, false, false, false, true, false, 'template', 'template'),
        (p_tenant_id, 'FOOD', 'Food', 'Food Reimbursement', 'FOOD', 'FLAT', 0, 3000.0000, false, false, false, false, true, false, 'template', 'template'),
        (p_tenant_id, 'FUEL', 'Fuel', 'Fuel Reimbursement', 'FUEL', 'FLAT', 0, 5000.0000, false, false, false, false, true, false, 'template', 'template')
    ON CONFLICT (tenant_id, code) DO NOTHING;
END;
$$;

REVOKE EXECUTE ON FUNCTION payroll.seed_default_reimbursements(UUID) FROM PUBLIC;

-- 2. Trigger function for future tenants
CREATE OR REPLACE FUNCTION payroll.tenant_seed_default_reimbursements()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, pg_temp
AS $$
BEGIN
    PERFORM payroll.seed_default_reimbursements(NEW.tenant_id);
    RETURN NEW;
END;
$$;

REVOKE EXECUTE ON FUNCTION payroll.tenant_seed_default_reimbursements() FROM PUBLIC;

DROP TRIGGER IF EXISTS tenant_seed_reimbursements ON core.tenant;
CREATE TRIGGER tenant_seed_reimbursements
    AFTER INSERT ON core.tenant
    FOR EACH ROW
    EXECUTE FUNCTION payroll.tenant_seed_default_reimbursements();

-- 3. Backfill all existing non-platform tenants
SELECT payroll.seed_default_reimbursements(t.tenant_id) FROM core.tenant t;

-- 4. Update country template for India in reference.country_template if present
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM reference.country_template
        WHERE country_code = 'IN' AND section = 'salary_components'
    ) THEN
        UPDATE reference.country_template
        SET payload = jsonb_set(
            payload,
            '{reimbursements}',
            $json$[
              {"code": "INTERNET", "name": "Internet", "display_name": "Internet Reimbursement", "reimbursement_type": "GENERAL", "calculation_type": "FLAT", "default_value": "0", "max_limit": "2000"},
              {"code": "TRAVEL", "name": "Travel", "display_name": "Travel Reimbursement", "reimbursement_type": "TRAVEL", "calculation_type": "FLAT", "default_value": "0", "max_limit": "5000"},
              {"code": "MEDICAL", "name": "Medical", "display_name": "Medical Reimbursement", "reimbursement_type": "MEDICAL", "calculation_type": "FLAT", "default_value": "0", "max_limit": "15000"},
              {"code": "FOOD", "name": "Food", "display_name": "Food Reimbursement", "reimbursement_type": "FOOD", "calculation_type": "FLAT", "default_value": "0", "max_limit": "3000"},
              {"code": "FUEL", "name": "Fuel", "display_name": "Fuel Reimbursement", "reimbursement_type": "FUEL", "calculation_type": "FLAT", "default_value": "0", "max_limit": "5000"}
            ]$json$::jsonb,
            true
        )
        WHERE country_code = 'IN' AND section = 'salary_components';
    END IF;
END;
$$;
