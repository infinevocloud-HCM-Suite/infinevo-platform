import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { OnboardingTab } from './OnboardingTab.jsx';
import { onboardingService } from '../../onboarding/onboardingService.js';
import * as useCanModule from '@shell/screens';

vi.mock('../../onboarding/onboardingService.js', () => ({
  onboardingService: {
    getEmployeeProgress: vi.fn(),
    assignTasks: vi.fn(),
    updateTask: vi.fn(),
  },
}));

vi.mock('@shared/ui/msgHelper.js', () => ({
  successMsg: vi.fn().mockResolvedValue(true),
  errorMsg: vi.fn().mockResolvedValue(true),
}));

describe('OnboardingTab', () => {
  const employeeId = 'emp-123';

  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(useCanModule, 'useCan').mockReturnValue(true);
  });

  it('renders empty alert and assign defaults button when total tasks is 0', async () => {
    onboardingService.getEmployeeProgress.mockResolvedValueOnce({
      employeeId,
      totalTasks: 0,
      completedTasks: 0,
      pendingTasks: 0,
      progressPercentage: 100,
      tasks: [],
    });

    render(<OnboardingTab employeeId={employeeId} />);

    await waitFor(() => {
      expect(screen.getAllByText(/No Onboarding Tasks Assigned/i)[0]).toBeDefined();
    });
    expect(screen.getByRole('button', { name: /assign default checklist/i })).toBeDefined();
  });

  it('renders tasks and progress meter when tasks are present', async () => {
    onboardingService.getEmployeeProgress.mockResolvedValueOnce({
      employeeId,
      totalTasks: 2,
      completedTasks: 1,
      pendingTasks: 1,
      progressPercentage: 50,
      tasks: [
        {
          id: 'task-1',
          employeeId,
          title: 'Submit Aadhaar Card',
          description: 'Upload valid ID',
          taskType: 'DOCUMENT_UPLOAD',
          isMandatory: true,
          dueDate: '2026-11-25',
          status: 'COMPLETED',
        },
        {
          id: 'task-2',
          employeeId,
          title: 'Code of Conduct',
          description: 'Sign agreement',
          taskType: 'POLICY_ACKNOWLEDGEMENT',
          isMandatory: true,
          dueDate: '2026-11-20',
          status: 'PENDING',
        },
      ],
    });

    render(<OnboardingTab employeeId={employeeId} />);

    expect(await screen.findByText('Submit Aadhaar Card')).toBeDefined();
    expect(screen.getByText('Code of Conduct')).toBeDefined();
    expect(screen.getByText(/1 of 2 tasks completed/i)).toBeDefined();
  });

  it('triggers updateTask when marking a task as complete', async () => {
    onboardingService.getEmployeeProgress.mockResolvedValue({
      employeeId,
      totalTasks: 1,
      completedTasks: 0,
      pendingTasks: 1,
      progressPercentage: 0,
      tasks: [
        {
          id: 'task-pending',
          employeeId,
          title: 'Orientation Handover',
          taskType: 'MANUAL_TASK',
          isMandatory: false,
          status: 'PENDING',
        },
      ],
    });
    onboardingService.updateTask.mockResolvedValueOnce({ id: 'task-pending', status: 'COMPLETED' });

    render(<OnboardingTab employeeId={employeeId} />);

    const completeBtn = await screen.findByRole('button', { name: /complete/i });
    fireEvent.click(completeBtn);

    await waitFor(() => {
      expect(onboardingService.updateTask).toHaveBeenCalledWith(
        employeeId,
        'task-pending',
        { status: 'COMPLETED' }
      );
    });
  });
});
