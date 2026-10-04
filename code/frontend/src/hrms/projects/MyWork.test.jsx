import { describe, it, expect, vi, beforeEach } from 'vitest';
import { configure, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MyWork, isOverdue } from './MyWork.jsx';
import { projectService } from './projectService.js';

configure({ asyncUtilTimeout: 30000 });

vi.mock('./projectService.js', async (orig) => {
  const real = await orig();
  return {
    ...real,
    projectService: { mine: vi.fn(), myTasks: vi.fn(), setTaskStatus: vi.fn() },
  };
});

const tasks = [
  {
    id: 't1',
    title: 'Late task',
    project_name: 'Apollo',
    due_date: '2020-01-01',
    priority: 'HIGH',
    status: 'TODO',
  },
  {
    id: 't2',
    title: 'Future task',
    project_name: 'Apollo',
    due_date: '2999-01-01',
    priority: 'LOW',
    status: 'TODO',
  },
];

describe('MyWork (W-48.1 §7)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    projectService.mine.mockResolvedValue([{ id: 'p1', name: 'Apollo', status: 'STARTED', progress: 50 }]);
    projectService.myTasks.mockResolvedValue(tasks);
    projectService.setTaskStatus.mockResolvedValue({
      ...tasks[1],
      status: 'IN_PROGRESS',
    });
  });

  it('marks the overdue task only', async () => {
    render(<MyWork />);
    expect(await screen.findByTestId('overdue-t1')).toBeTruthy();
    expect(screen.queryByTestId('overdue-t2')).toBeNull();
    expect(isOverdue({ due_date: '2020-01-01', status: 'COMPLETED' })).toBe(false);
  }, 60000);

  it('a status change calls the status endpoint', async () => {
    render(<MyWork />);
    const select = await screen.findByRole('combobox', {
      name: 'Status of Future task',
    });
    fireEvent.mouseDown(select);
    const pick = () => [...document.querySelectorAll('.ant-select-item-option[title="In progress"]')].pop();
    await waitFor(() => expect(pick()).toBeTruthy());
    fireEvent.click(pick());
    await waitFor(() => expect(projectService.setTaskStatus).toHaveBeenCalledWith('t2', 'IN_PROGRESS'));
  }, 60000);
});
