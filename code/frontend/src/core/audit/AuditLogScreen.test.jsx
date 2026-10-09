import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import * as shellScreens from '@shell/screens';
import { AuditLogScreen } from './AuditLogScreen.jsx';
import { auditService } from '../admin/auditService.js';

vi.mock('../admin/auditService.js', () => ({
  auditService: { search: vi.fn() },
}));

// A bare Spring page of AuditLogView rows (shared/audit/AuditQueryService).
const PAGE = {
  content: [
    {
      id: 'a-1',
      occurredAt: '2026-10-01T09:30:00Z',
      actorUserId: 'u-1',
      actorLabel: 'asha@acme.test',
      operation: 'UPDATE',
      entitySchema: 'core',
      entityTable: 'employee',
      entityId: 'e-1',
      changedColumns: ['first_name', 'last_name'],
      oldValues: {},
      newValues: {},
      traceId: 'tr-1',
    },
  ],
  totalElements: 1,
  number: 0,
  size: 20,
};

function can(actions) {
  vi.spyOn(shellScreens, 'useCan').mockImplementation((action) => actions.includes(action));
}

function renderScreen() {
  return render(
    <MemoryRouter initialEntries={['/audit']}>
      <AuditLogScreen />
    </MemoryRouter>,
  );
}

describe('AuditLogScreen (D-73)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    auditService.search.mockResolvedValue(PAGE);
  });

  it("shows the signed-in tenant's audit trail under the title Audit log", async () => {
    can(['core.audit.read']);
    renderScreen();

    expect(screen.getByRole('heading', { name: 'Audit log' })).toBeDefined();
    await waitFor(() => expect(screen.getByText('asha@acme.test')).toBeDefined());
    expect(screen.getByText('employee')).toBeDefined();
    expect(screen.getByText('first_name, last_name')).toBeDefined();
    expect(auditService.search).toHaveBeenCalledWith(expect.objectContaining({ page: 0 }));
  });

  it('shows Not entitled without core.audit.read and asks the server nothing', () => {
    can([]);
    renderScreen();

    expect(screen.queryByRole('heading', { name: 'Audit log' })).toBeNull();
    expect(auditService.search).not.toHaveBeenCalled();
  });
});
