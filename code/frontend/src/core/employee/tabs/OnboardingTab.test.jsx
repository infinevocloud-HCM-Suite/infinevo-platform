import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { OnboardingTab } from './OnboardingTab.jsx';
import { onboardingService } from '../../onboarding/onboardingService.js';
import { documentService } from '../../document/documentService.js';
import * as useCanModule from '@shell/screens';

vi.mock('../../onboarding/onboardingService.js', () => ({
  onboardingService: {
    getEmployeeProgress: vi.fn(),
    assignTasks: vi.fn(),
    updateTask: vi.fn(),
  },
}));

vi.mock('../../document/documentService.js', () => ({
  documentService: {
    link: vi.fn(),
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

  it('renders error alert with retry button on load failure', async () => {
    onboardingService.getEmployeeProgress.mockRejectedValueOnce(new Error('Fetch failed'));

    render(<OnboardingTab employeeId={employeeId} />);

    expect(await screen.findByText('Failed to load onboarding checklist')).toBeDefined();
    const retryBtn = screen.getByRole('button', { name: /retry/i });

    onboardingService.getEmployeeProgress.mockResolvedValueOnce({
      employeeId,
      totalTasks: 0,
      completedTasks: 0,
      pendingTasks: 0,
      progressPercentage: 100,
      tasks: [],
    });
    fireEvent.click(retryBtn);

    await waitFor(() => {
      expect(screen.getAllByText(/No Onboarding Tasks Assigned/i)[0]).toBeDefined();
    });
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

  it('allows viewing document and rejecting submitted document task with remarks', async () => {
    onboardingService.getEmployeeProgress.mockResolvedValue({
      employeeId,
      totalTasks: 1,
      completedTasks: 0,
      pendingTasks: 1,
      progressPercentage: 0,
      tasks: [
        {
          id: 'task-sub-1',
          employeeId,
          title: 'Submit Driving License',
          taskType: 'DOCUMENT_UPLOAD',
          documentId: 'doc-dl-1',
          isMandatory: true,
          status: 'SUBMITTED',
        },
      ],
    });
    documentService.link.mockResolvedValueOnce({ url: 'https://storage.example/dl.pdf' });
    onboardingService.updateTask.mockResolvedValueOnce({ id: 'task-sub-1', status: 'PENDING' });

    render(<OnboardingTab employeeId={employeeId} />);

    expect(await screen.findByText('Submit Driving License')).toBeDefined();

    // View document button
    const viewDocBtn = screen.getByRole('button', { name: /view document/i });
    fireEvent.click(viewDocBtn);
    await waitFor(() => {
      expect(documentService.link).toHaveBeenCalledWith('doc-dl-1');
    });

    // Reject button
    const rejectBtn = screen.getByRole('button', { name: /reject/i });
    fireEvent.click(rejectBtn);

    expect(await screen.findByText('Reject Submitted Document')).toBeDefined();
    const textarea = screen.getByPlaceholderText(/document copy is blurred/i);
    fireEvent.change(textarea, { target: { value: 'Scan is blurry, please re-upload.' } });

    const confirmRejectBtn = screen.getByRole('button', { name: 'Reject Document' });
    fireEvent.click(confirmRejectBtn);

    await waitFor(() => {
      expect(onboardingService.updateTask).toHaveBeenCalledWith(
        employeeId,
        'task-sub-1',
        {
          status: 'PENDING',
          remarks: 'Scan is blurry, please re-upload.',
        }
      );
    });
  });

  it('allows reopening a completed task', async () => {
    onboardingService.getEmployeeProgress.mockResolvedValue({
      employeeId,
      totalTasks: 1,
      completedTasks: 1,
      pendingTasks: 0,
      progressPercentage: 100,
      tasks: [
        {
          id: 'task-done-1',
          employeeId,
          title: 'Submit Passport',
          taskType: 'DOCUMENT_UPLOAD',
          isMandatory: true,
          status: 'COMPLETED',
        },
      ],
    });
    onboardingService.updateTask.mockResolvedValueOnce({ id: 'task-done-1', status: 'PENDING' });

    render(<OnboardingTab employeeId={employeeId} />);

    expect(await screen.findByText('Submit Passport')).toBeDefined();
    const reopenBtn = screen.getByRole('button', { name: /reopen/i });
    fireEvent.click(reopenBtn);

    const reopenBtns = await screen.findAllByRole('button', { name: /reopen/i });
    fireEvent.click(reopenBtns[reopenBtns.length - 1]);

    await waitFor(() => {
      expect(onboardingService.updateTask).toHaveBeenCalledWith(
        employeeId,
        'task-done-1',
        { status: 'PENDING' }
      );
    });
  });

  it('allows waiving a pending task', async () => {
    onboardingService.getEmployeeProgress.mockResolvedValue({
      employeeId,
      totalTasks: 1,
      completedTasks: 0,
      pendingTasks: 1,
      progressPercentage: 0,
      tasks: [
        {
          id: 'task-opt-1',
          employeeId,
          title: 'Optional Car Parking Form',
          taskType: 'MANUAL_TASK',
          isMandatory: false,
          status: 'PENDING',
        },
      ],
    });
    onboardingService.updateTask.mockResolvedValueOnce({ id: 'task-opt-1', status: 'WAIVED' });

    render(<OnboardingTab employeeId={employeeId} />);

    expect(await screen.findByText('Optional Car Parking Form')).toBeDefined();
    const waiveBtn = screen.getByRole('button', { name: /waive/i });
    fireEvent.click(waiveBtn);

    const popconfirmWaiveBtn = await screen.findByRole('button', { name: 'Waive' });
    fireEvent.click(popconfirmWaiveBtn);

    await waitFor(() => {
      expect(onboardingService.updateTask).toHaveBeenCalledWith(
        employeeId,
        'task-opt-1',
        { status: 'WAIVED' }
      );
    });
  });
});
