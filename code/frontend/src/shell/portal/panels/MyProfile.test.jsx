import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MyProfile } from './MyProfile.jsx';
import { apiClient } from '../../../shared/api/client.js';

// The bodies below are the server's own shapes (D-77). GET /api/v1/me/employee answers a bare
// EmployeeResponse (camelCase record components, no envelope); the org masters answer bare lists
// of DepartmentResponse / DesignationResponse / WorkLocationResponse.
const EMPLOYEE = {
  id: 'emp-1',
  tenantId: 'tenant-1',
  employeeNumber: 'EMP-001',
  firstName: 'Asha',
  middleName: 'Kumari',
  lastName: 'Rao',
  gender: 'MALE',
  dateOfJoining: '2026-04-01',
  terminationDate: null,
  status: 'ACTIVE',
  workEmail: 'asha.rao@acme.example',
  mobile: '+91 98450 00000',
  portalEnabled: true,
  userAccountId: 'user-1',
  departmentId: '6f1c2a10-0000-4000-8000-000000000001',
  designationId: '6f1c2a10-0000-4000-8000-000000000002',
  workLocationId: '6f1c2a10-0000-4000-8000-000000000003',
  createdAt: '2026-04-01T09:00:00Z',
  updatedAt: '2026-04-01T09:00:00Z',
};

const master = (id, code, name) => ({ id, tenantId: 'tenant-1', code, name, active: true });

const LISTS = {
  '/v1/departments': [master(EMPLOYEE.departmentId, 'ENG', 'Engineering')],
  '/v1/designations': [master(EMPLOYEE.designationId, 'SE', 'Software Engineer')],
  '/v1/work-locations': [{ ...master(EMPLOYEE.workLocationId, 'BLR', 'Bengaluru HQ'), city: 'Bengaluru' }],
};

function serve(refused = []) {
  return vi.spyOn(apiClient, 'get').mockImplementation(async (url) => {
    if (url === '/v1/me/employee') return { data: EMPLOYEE };
    if (refused.includes(url)) {
      throw { code: 'FORBIDDEN', message: 'Not allowed', status: 403, isForbidden: true };
    }
    if (LISTS[url]) return { data: LISTS[url] };
    throw new Error(`unexpected GET ${url}`);
  });
}

// The value cell beside a Descriptions label.
const valueOf = (label) => screen.getByText(label).closest('th').nextElementSibling.textContent;

describe('MyProfile shows names and words, not ids and codes (D-77)', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('names the department, designation and work location, and formats status, gender and dates', async () => {
    serve();

    render(<MyProfile />);

    expect(await screen.findByText('Engineering')).toBeDefined();
    expect(screen.getByText('Software Engineer')).toBeDefined();
    expect(screen.getByText('Bengaluru HQ')).toBeDefined();
    expect(screen.getAllByText('Asha Kumari Rao').length).toBeGreaterThan(0);
    expect(valueOf('Status')).toBe('Active');
    expect(valueOf('Gender')).toBe('Male');
    expect(screen.getByText('1 Apr 2026')).toBeDefined();
    expect(screen.getByText('asha.rao@acme.example')).toBeDefined();
    expect(screen.getByText('+91 98450 00000')).toBeDefined();
    expect(screen.getByText('Job')).toBeDefined();
    expect(screen.getByText('Contact')).toBeDefined();
    // The number is shown once, and no UUID or raw code reaches the page.
    expect(screen.getAllByText('EMP-001')).toHaveLength(1);
    expect(screen.queryByText(EMPLOYEE.departmentId)).toBeNull();
    expect(screen.queryByText('ACTIVE')).toBeNull();
    expect(screen.queryByText('MALE')).toBeNull();
    expect(screen.queryByText('2026-04-01')).toBeNull();
  });

  it('shows a dash for the one field whose list is refused, and the others by name', async () => {
    serve(['/v1/designations']);

    render(<MyProfile />);

    expect(await screen.findByText('Engineering')).toBeDefined();
    expect(screen.getByText('Bengaluru HQ')).toBeDefined();
    expect(valueOf('Designation')).toBe('—');
    expect(screen.queryByText(EMPLOYEE.designationId)).toBeNull();
  });
});
