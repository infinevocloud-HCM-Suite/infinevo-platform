# Feature: Asset Tracking

| Field | Value |
|---|---|
| **Feature ID** | `W-111` · `HRMS-ASSET-TRACKING` |
| **Owner** | sayeed (`dev-sayeed`) |
| **Apps touched** | `code/backend/hrms`, `code/backend/migration`, `code/frontend` (`src/hrms/assets`, `src/core/employee`, `src/shell/portal`) |
| **Related gaps** | `DEV-TRACKER.md` row `W-111`; no hardware or equipment inventory or employee assignment tracking exists today |
| **Status** | Ready |
| **Blocked by** | nothing (`core.employee` and employee portal exist) |

---

## 1. What and why

Modern organizations distribute significant physical hardware and equipment to employees—laptops, monitors, mobile devices, access cards, and peripherals:
1. **Lost Equipment & Inadequate Recovery:** Without a centralized system, company assets are tracked across disparate spreadsheets or not at all. When employees resign or are terminated, HR and IT have no verified inventory of what equipment must be retrieved before completing full & final settlement (`W-90`).
2. **Missing Warranty & Lifecycle Visibility:** Operations teams struggle to track serial numbers, purchase dates, warranty expiry, or equipment conditions across distributed and remote teams.
3. **No Employee Self-Service Record:** Employees have no official record of company property assigned to their custody, leading to confusion during office relocations or equipment upgrades.

This feature delivers **Asset Tracking** in `hrms`:
1. **Central Asset Registry:** Catalog all company assets with unique asset tags, serial numbers, categories, specifications, and warranty details.
2. **Assignment & Custody Tracking:** Assign assets to employees, record handover conditions, track assignment dates, and maintain an immutable historical chain of custody.
3. **Return & Exit Clearance:** Seamless asset return workflow recording item condition upon return; exposes an exit clearance API used during employee separation.
4. **Employee Self-Service (`/me/assets`):** Employees can view the active hardware and equipment assigned to them in their portal dashboard.

---

## 2. Size Cap

| Axis | This spec | Limit |
|---|---|---|
| Backend module | `hrms` (references `core.employee` for assignment custody) | 1 |
| Flyway migration | `hrms/V195__asset_tracking.sql` (tables for company asset registry and assignment history) | 1 |
| Externally testable behaviour | Admin adds an asset, assigns it to an employee, employee views it in portal, and admin records asset return | 1 |
| Frontend area | `code/frontend/src/hrms/assets` (admin registry) and `src/shell/portal/panels/MyAssets.jsx` (self-service) | 1 |

---

## 3. Scope

### In Scope
1. **Company Asset Registry (`hrms.company_asset`):**
   - Unique Asset Tag (e.g., `AST-001`, `LAP-2026-042`).
   - Name and description (e.g., "Apple MacBook Pro 14\"", "Dell UltraSharp 27\"").
   - Category: `LAPTOP`, `MONITOR`, `MOBILE_DEVICE`, `ACCESS_CARD`, `PERIPHERAL`, `VEHICLE`, `FURNITURE`, `OTHER`.
   - Brand, model, and manufacturer serial number.
   - Purchase date, vendor name, purchase cost (`NUMERIC(19,4)`), and warranty expiry date.
   - Status: `AVAILABLE`, `ASSIGNED`, `UNDER_REPAIR`, `RETIRED`, `LOST`.
   - Condition: `NEW`, `GOOD`, `FAIR`, `DAMAGED`.
2. **Asset Assignment & Custody History (`hrms.asset_assignment`):**
   - Links asset to an active employee (`core.employee`).
   - Assignment details: `assigned_date`, `assigned_by`, condition at handover, assignment notes.
   - Return details: `returned_date`, `returned_by`, condition upon return, return notes.
   - Active status flag (`is_active`): ensures an asset can only have at most one active assignment at any point in time.
3. **Exit Clearance Check (`GET /api/v1/hrms/employees/{id}/assets/active`):**
   - Lists all unreturned assets held by an employee; returns `clearance_status` (`CLEAR` if 0 active assets, `PENDING_RETURN` otherwise).
4. **Frontend UI:**
   - **Asset Inventory Page (`/hrms/assets`):** Filter by category and status (`AVAILABLE`, `ASSIGNED`), view custodian, and register new assets.
   - **Asset Details Drawer:** View asset specs, current custodian, and complete historical custody timeline.
   - **Assign / Return Modals:** Handover workflow with condition checklist and return intake form.
   - **Employee Portal (`/me/assets`):** Employee self-service card showing assigned assets, serial numbers, and handover dates.
   - **Employee Profile Tab (`/employees/:id`):** Dedicated "Assets" tab on the employee details screen.

### Out of Scope
1. **Automated MDM / Device Agent Integration:** Direct Jamf, Microsoft Intune, or Google Workspace device-level sync (belongs to external integration stream `W-108`).
2. **Depreciation & Capital Asset Accounting:** Fixed asset accounting ledgers and tax depreciation belong to ERP / accounting integration (`W-107`).

---

## 4. Domain Model & Database Schema

### Migration: `hrms/V195__asset_tracking.sql`

```sql
-- Migration: V195__asset_tracking.sql
-- Description: W-111 Asset tracking inventory and employee assignment history with PostgreSQL RLS isolation

-- 1. Company Asset Inventory
CREATE TABLE hrms.company_asset (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    asset_tag VARCHAR(64) NOT NULL,
    asset_name VARCHAR(120) NOT NULL,
    category VARCHAR(32) NOT NULL DEFAULT 'LAPTOP',
    brand VARCHAR(60),
    model_number VARCHAR(80),
    serial_number VARCHAR(100),
    purchase_date DATE,
    purchase_cost NUMERIC(19,4),
    warranty_expiry DATE,
    vendor_name VARCHAR(120),
    current_status VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE',
    item_condition VARCHAR(32) NOT NULL DEFAULT 'GOOD',
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system',
    CONSTRAINT uk_company_asset_tag UNIQUE (tenant_id, asset_tag)
);

ALTER TABLE hrms.company_asset ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON hrms.company_asset
    USING (tenant_id = CASE
      WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
      WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
      ELSE current_setting('app.current_tenant_id', true)::uuid
    END);
CREATE INDEX idx_company_asset_tenant_status ON hrms.company_asset(tenant_id, current_status, category);
CREATE INDEX idx_company_asset_serial ON hrms.company_asset(tenant_id, serial_number);

-- 2. Asset Assignment & History
CREATE TABLE hrms.asset_assignment (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES core.tenant(tenant_id),
    asset_id UUID NOT NULL REFERENCES hrms.company_asset(id) ON DELETE CASCADE,
    employee_id UUID NOT NULL REFERENCES core.employee(id),
    assigned_date DATE NOT NULL,
    assigned_by UUID REFERENCES core.employee(id),
    assignment_condition VARCHAR(32) NOT NULL DEFAULT 'GOOD',
    assignment_notes TEXT,
    returned_date DATE,
    returned_by UUID REFERENCES core.employee(id),
    return_condition VARCHAR(32),
    return_notes TEXT,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(100) NOT NULL DEFAULT 'system'
);

ALTER TABLE hrms.asset_assignment ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON hrms.asset_assignment
    USING (tenant_id = CASE
      WHEN current_setting('app.current_tenant_id', true) IS NULL THEN NULL
      WHEN current_setting('app.current_tenant_id', true) = '' THEN NULL
      ELSE current_setting('app.current_tenant_id', true)::uuid
    END);
CREATE INDEX idx_asset_assignment_employee ON hrms.asset_assignment(tenant_id, employee_id, is_active);
CREATE INDEX idx_asset_assignment_asset ON hrms.asset_assignment(tenant_id, asset_id, is_active);

-- Enforce at most ONE active assignment per asset at any time
CREATE UNIQUE INDEX uk_asset_single_active_assignment
    ON hrms.asset_assignment (tenant_id, asset_id)
    WHERE is_active = true;
```

---

## 5. Backend Architecture & API Contracts

### Package: `com.infinevo.hrms.asset`

| Layer | Class | Responsibility |
|---|---|---|
| Entities | `CompanyAsset`, `AssetAssignment` | Hibernate entities mapped to `hrms` schema |
| Repositories | `CompanyAssetRepository`, `AssetAssignmentRepository` | Spring Data JPA queries with tenant RLS isolation |
| Services | `AssetService`, `AssetAssignmentService` | Asset lifecycle, assignment validation, return processing, and exit clearance |
| Controllers | `AssetController` (`/api/v1/hrms/assets`), `MyAssetController` (`/api/v1/me/assets`) | Admin management and employee self-service endpoints |

---

### Core API Contracts

| Method | Path | Permission | Description |
|---|---|---|---|
| `GET` | `/api/v1/hrms/assets` | `hrms.asset.read` | List assets with category, status, and search filters |
| `POST` | `/api/v1/hrms/assets` | `hrms.asset.manage` | Register new asset in inventory |
| `GET` | `/api/v1/hrms/assets/{id}` | `hrms.asset.read` | Get asset details, current custodian, and custody history |
| `PUT` | `/api/v1/hrms/assets/{id}` | `hrms.asset.manage` | Update asset details, warranty, or condition |
| `POST` | `/api/v1/hrms/assets/{id}/assign` | `hrms.asset.manage` | Assign available asset to an employee |
| `POST` | `/api/v1/hrms/assets/{id}/return` | `hrms.asset.manage` | Record asset return from employee with condition assessment |
| `PATCH` | `/api/v1/hrms/assets/{id}/status` | `hrms.asset.manage` | Update status (e.g. mark `UNDER_REPAIR`, `RETIRED`, `LOST`) |
| `GET` | `/api/v1/hrms/employees/{id}/assets` | `hrms.asset.read` | View all assets currently assigned to employee (exit clearance) |
| `GET` | `/api/v1/me/assets` | (authenticated user) | View assets assigned to the signed-in employee |

---

## 6. Frontend Architecture

### 1. HR Asset Management (`code/frontend/src/hrms/assets/`)
- `AssetList.jsx`: Searchable table with category filters, status pills (`Available`, `Assigned`, `In Repair`), and quick assignment action.
- `AssetDrawer.jsx`: Create and edit asset specifications, purchase cost, and warranty expiry.
- `AssetDetail.jsx`: Overview card, current holder, and vertical timeline of historical assignments.
- `AssignModal.jsx`: Select employee from dropdown, record handover condition, and assign.
- `ReturnModal.jsx`: Record return date, inspect condition (`Good`, `Damaged`), and accept return.
- Registered route: `/hrms/assets` and `/hrms/assets/:id`.

### 2. Employee Profile & Portal
- `src/core/employee/tabs/AssetsTab.jsx`: Tab on employee page (`/employees/:id/assets`) displaying all assigned equipment.
- `src/shell/portal/panels/MyAssets.jsx`: Self-service view under `/me/assets` allowing employees to verify their assigned company equipment.

---

## 7. Impact on Standing Rules

- **`tenant_id` & RLS:** Both `hrms.company_asset` and `hrms.asset_assignment` carry `tenant_id` and strict PostgreSQL RLS policies.
- **Flyway Migrations:** Exactly 1 migration (`hrms/V195__asset_tracking.sql`). All schema references explicitly qualified.
- **Precision:** `purchase_cost` uses `NUMERIC(19,4)` and `BigDecimal` / `Money`. Floating point arithmetic is prohibited.
- **Indexes:** Indexed on `(tenant_id, current_status, category)` and `(tenant_id, employee_id, is_active)`. Partial unique index enforces single active assignment per asset.
- **Expand / Contract:** Creates clean new tables; zero destructive changes.
- **Module Boundaries:** `hrms` references `core.employee` via foreign keys and service contracts.

---

## 8. Automated Tests

| Test Class | Scope | What It Proves |
|---|---|---|
| `AssetServiceTest.java` (Unit) | Backend | Verifies asset creation, uniqueness of `asset_tag`, and status lifecycle. |
| `AssetAssignmentTest.java` (Unit) | Backend | Proves asset assignment transitions status from `AVAILABLE` to `ASSIGNED`; proves attempt to double-assign throws `409 Conflict`. |
| `AssetReturnTest.java` (Unit) | Backend | Proves return transitions status back to `AVAILABLE`, updates assignment `is_active = false`, and records return condition. |
| `AssetClearanceTest.java` (Unit) | Backend | Verifies exit clearance returns all active unreturned assets for an employee. |
| `AssetList.test.jsx` (Component) | Frontend | Verifies asset table rendering, status filter, and assignment trigger. |
| `MyAssets.test.jsx` (Component) | Frontend | Verifies employee portal displays assigned laptops and hardware correctly. |

---

## 9. Verification Commands

```bash
# 1. Backend build, spotless and unit tests
cd code/backend
mvn spotless:check -pl hrms
mvn test -pl hrms "-Dtest=AssetServiceTest,AssetAssignmentTest,AssetReturnTest,AssetClearanceTest"

# 2. Frontend lint and tests
cd ../frontend
npm run lint
npm test -- src/hrms/assets/AssetList.test.jsx
```

