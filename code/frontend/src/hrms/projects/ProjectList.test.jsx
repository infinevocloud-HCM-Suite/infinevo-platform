import { describe, it, expect, vi, beforeEach } from 'vitest';
import { configure, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { ProjectList } from './ProjectList.jsx';
import { projectService } from './projectService.js';
import { useCan } from '@shell/screens';

configure({ asyncUtilTimeout: 30000 });

vi.mock('./projectService.js', async (orig) => {
  const real = await orig();
  return {
    ...real,
    projectService: { list: vi.fn(), create: vi.fn(), assignable: vi.fn() },
  };
});

vi.mock('@shell/screens', () => ({
  useCan: vi.fn(() => false),
  NotEntitled: () => <div>Not entitled</div>,
}));

const rows = [
  {
    id: 'p1',
    name: 'Apollo',
    manager_name: 'Mira Manager',
    status: 'STARTED',
    progress: 30,
    end_date: '2026-12-31',
    team: [
      { employee_id: 'e1', name: 'A' },
      { employee_id: 'e2', name: 'B' },
    ],
  },
];

function grant(...actions) {
  useCan.mockImplementation((a) => actions.includes(a));
}

const renderList = () =>
  render(
    <MemoryRouter>
      <ProjectList />
    </MemoryRouter>
  );

describe('ProjectList (W-48.1 §7)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    projectService.list.mockResolvedValue(rows);
  });

  it('a manager without hrms.project.read sends managed=true and sees no create', async () => {
    grant();
    renderList();
    expect(await screen.findByText('Apollo')).toBeTruthy();
    expect(screen.getByText('Mira Manager')).toBeTruthy();
    expect(projectService.list.mock.calls[0][0].managed).toBe(true);
    expect(screen.queryByRole('button', { name: 'New project' })).toBeNull();
  }, 60000);

  it('HR with read and manage lists all and can create', async () => {
    grant('hrms.project.read', 'hrms.project.manage');
    renderList();
    expect(await screen.findByRole('button', { name: 'New project' })).toBeTruthy();
    await waitFor(() => expect(projectService.list).toHaveBeenCalled());
    expect(projectService.list.mock.calls[0][0].managed).toBeUndefined();
  }, 60000);
});
