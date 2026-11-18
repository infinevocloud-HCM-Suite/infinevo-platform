import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { MyOnboarding } from './MyOnboarding.jsx';
import { onboardingService } from '../../../core/onboarding/onboardingService.js';

vi.mock('../../../core/onboarding/onboardingService.js', () => ({
  onboardingService: {
    getMyProgress: vi.fn(),
    updateTask: vi.fn(),
  },
}));

vi.mock('@shared/ui/msgHelper.js', () => ({
  successMsg: vi.fn().mockResolvedValue(true),
  errorMsg: vi.fn().mockResolvedValue(true),
}));

describe('MyOnboarding', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders empty alert when employee has no tasks assigned', async () => {
    onboardingService.getMyProgress.mockResolvedValueOnce({
      totalTasks: 0,
      completedTasks: 0,
      pendingTasks: 0,
      progressPercentage: 100,
      tasks: [],
    });

    render(
      <MemoryRouter>
        <MyOnboarding />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getAllByText(/No Onboarding Tasks/i)[0]).toBeDefined();
    });
  });

  it('renders employee tasks and allows marking a manual task done', async () => {
    onboardingService.getMyProgress.mockResolvedValue({
      totalTasks: 1,
      completedTasks: 0,
      pendingTasks: 1,
      progressPercentage: 0,
      tasks: [
        {
          id: 'task-self-1',
          employeeId: 'emp-me',
          title: 'Review Workstation & IT Setup',
          description: 'Confirm laptop working properly',
          taskType: 'MANUAL_TASK',
          isMandatory: false,
          status: 'PENDING',
        },
      ],
    });
    onboardingService.updateTask.mockResolvedValueOnce({ id: 'task-self-1', status: 'COMPLETED' });

    render(
      <MemoryRouter>
        <MyOnboarding />
      </MemoryRouter>
    );

    expect(await screen.findByText('Review Workstation & IT Setup')).toBeDefined();
    const doneBtn = screen.getByRole('button', { name: /mark as done/i });
    fireEvent.click(doneBtn);

    await waitFor(() => {
      expect(onboardingService.updateTask).toHaveBeenCalledWith(
        'emp-me',
        'task-self-1',
        expect.objectContaining({ status: 'COMPLETED' })
      );
    });
  });
});
