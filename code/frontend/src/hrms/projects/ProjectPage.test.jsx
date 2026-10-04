import { describe, it, expect, vi, beforeEach } from 'vitest';
import { configure, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { ProjectPage } from './ProjectPage.jsx';
import { projectService } from './projectService.js';
import { useCan } from '@shell/screens';

configure({ asyncUtilTimeout: 30000 });

vi.mock('./projectService.js', async (orig) => {
  const real = await orig();
  const fns = ['get', 'assignments', 'remove', 'tasks', 'setStatus', 'setProgress', 'update', 'assignable'];
  return {
    ...real,
    projectService: Object.fromEntries(fns.map((f) => [f, vi.fn()])),
  };
});

vi.mock('@shell/screens', () => ({
  useCan: vi.fn(() => true),
  NotEntitled: () => <div>Not entitled</div>,
}));

const project = {
  id: 'p1',
  name: 'Apollo',
  status: 'STARTED',
  progress: 20,
  priority: 'HIGH',
  manager_name: 'Mira',
  team: [
    { employee_id: 'e1', name: 'Ann Team' },
    { employee_id: 'e2', name: 'Bob Team' },
  ],
};

const renderPage = () =>
  render(
    <MemoryRouter initialEntries={['/hrms/projects/p1']}>
      <Routes>
        <Route path="/hrms/projects/:id" element={<ProjectPage />} />
      </Routes>
    </MemoryRouter>
  );

describe('ProjectPage (W-48.1 §7)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useCan.mockImplementation(() => true);
    projectService.get.mockResolvedValue(project);
    projectService.assignments.mockResolvedValue([
      {
        employee_id: 'e1',
        employee_name: 'Ann Team',
        assigned_on: '2026-09-01',
      },
      {
        employee_id: 'e2',
        employee_name: 'Bob Team',
        assigned_on: '2026-09-01',
      },
    ]);
    projectService.tasks.mockResolvedValue([]);
  });

  it('delete 409 shows the server message', async () => {
    projectService.remove.mockRejectedValue({
      response: {
        status: 409,
        data: { status: 409, message: 'Project is used by a live timesheet' },
      },
    });
    renderPage();
    fireEvent.click(await screen.findByRole('button', { name: 'Delete' }));
    const popup = await screen.findByRole('tooltip');
    fireEvent.click(within(popup).getByRole('button', { name: 'Delete' }));
    expect(await screen.findByText('Project is used by a live timesheet')).toBeTruthy();
    expect(projectService.remove).toHaveBeenCalledWith('p1');
  }, 60000);

  it('task assignee options are the team only', async () => {
    renderPage();
    fireEvent.click(await screen.findByRole('tab', { name: 'Tasks' }));
    fireEvent.click(await screen.findByRole('button', { name: 'New task' }));
    const assignee = await screen.findByRole('combobox', { name: 'Assignee' });
    fireEvent.mouseDown(assignee);
    await waitFor(() => expect(document.querySelectorAll('.ant-select-item-option').length).toBe(2));
    const names = [...document.querySelectorAll('.ant-select-item-option')].map((o) =>
      o.getAttribute('title')
    );
    expect(names).toEqual(['Ann Team', 'Bob Team']);
    expect(projectService.assignable).not.toHaveBeenCalled();
  }, 60000);
});
