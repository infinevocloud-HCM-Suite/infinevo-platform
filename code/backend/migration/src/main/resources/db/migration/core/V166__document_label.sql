-- Migration: V166__document_label.sql
-- Schema: core
-- Purpose: W-73.5 core.document.label — what an EMPLOYEE_DOCUMENT is (ID proof, address proof,
--          offer letter, contract, certificate, other), chosen by HR when the file is uploaded and
--          shown on the employee page's Documents tab and on the employee's /me Documents panel.
--
-- One nullable column, not a sub-kind: the kind stays EMPLOYEE_DOCUMENT and every existing row,
-- of every kind, stays valid with a NULL label. The vocabulary is checked here as well as in Java
-- (DocumentLabel), the way V037 checks kind, so a row written outside the service cannot hold a
-- value the enum cannot read back.
--
-- The spec first assumed an existing metadata map on core.document; there is none (V037), so this
-- column is the label's home. No new table: tenant_id and the tenant_isolation policy already
-- cover every row (V037).

ALTER TABLE core.document
    ADD COLUMN label VARCHAR(32) NULL;

-- A label only on an employee document: the service refuses one on any other kind
-- (DocumentServiceImpl.accept), and the database refuses it too.
ALTER TABLE core.document
    ADD CONSTRAINT document_label_check CHECK (
        label IS NULL
        OR (kind = 'EMPLOYEE_DOCUMENT'
            AND label IN ('ID_PROOF', 'ADDRESS_PROOF', 'OFFER_LETTER', 'CONTRACT', 'CERTIFICATE', 'OTHER')));

COMMENT ON COLUMN core.document.label IS
    'W-73.5: what an EMPLOYEE_DOCUMENT is - ID_PROOF, ADDRESS_PROOF, OFFER_LETTER, CONTRACT, CERTIFICATE or OTHER. NULL for every other kind.';
