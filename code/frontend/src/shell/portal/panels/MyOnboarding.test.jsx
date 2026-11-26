import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { MyOnboarding } from './MyOnboarding.jsx';
import { onboardingService } from '../../../core/onboarding/onboardingService.js';
import { documentService } from '../../../core/document/documentService.js';

vi.mock('../../../core/onboarding/onboardingService.js', () => ({
  onboardingService: {
    getMyProgress: vi.fn(),
    updateTask: vi.fn(),
  },
}));

vi.mock('../../../core/document/documentService.js', () => ({
  documentService: {
    uploadForEmployee: vi.fn(),
    link: vi.fn(),
  },
  MAX_DOCUMENT_BYTES: 10 * 1024 * 1024,
  ACCEPTED_DOCUMENT_TYPES: '.pdf,.png,.jpg,.jpeg',
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

  it('renders error alert with retry button on load failure', async () => {
    onboardingService.getMyProgress.mockRejectedValueOnce(new Error('Load error'));

    render(
      <MemoryRouter>
        <MyOnboarding />
      </MemoryRouter>
    );

    expect(await screen.findByText('Failed to load onboarding checklist')).toBeDefined();
    const retryBtn = screen.getByRole('button', { name: /retry/i });

    onboardingService.getMyProgress.mockResolvedValueOnce({
      totalTasks: 0,
      completedTasks: 0,
      pendingTasks: 0,
      progressPercentage: 100,
      tasks: [],
    });
    fireEvent.click(retryBtn);

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
          assignedRole: 'EMPLOYEE',
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

  it('renders task assigned to MANAGER as read-only for employee', async () => {
    onboardingService.getMyProgress.mockResolvedValueOnce({
      totalTasks: 1,
      completedTasks: 0,
      pendingTasks: 1,
      progressPercentage: 0,
      tasks: [
        {
          id: 'task-mgr-1',
          employeeId: 'emp-me',
          title: 'Manager 1-on-1 Check-in',
          taskType: 'MANUAL_TASK',
          assignedRole: 'MANAGER',
          isMandatory: true,
          status: 'PENDING',
        },
      ],
    });

    render(
      <MemoryRouter>
        <MyOnboarding />
      </MemoryRouter>
    );

    expect(await screen.findByText('Manager 1-on-1 Check-in')).toBeDefined();
    expect(screen.getByText('Read-only (MANAGER)')).toBeDefined();
    expect(screen.queryByRole('button', { name: /mark as done/i })).toBeNull();
  });

  it('allows uploading a document and submits task with returned documentId', async () => {
    onboardingService.getMyProgress.mockResolvedValue({
      totalTasks: 1,
      completedTasks: 0,
      pendingTasks: 1,
      progressPercentage: 0,
      tasks: [
        {
          id: 'task-doc-1',
          employeeId: 'emp-me',
          title: 'Submit Passport Copy',
          taskType: 'DOCUMENT_UPLOAD',
          documentLabel: 'ID_PROOF',
          assignedRole: 'EMPLOYEE',
          isMandatory: true,
          status: 'PENDING',
        },
      ],
    });
    documentService.uploadForEmployee.mockResolvedValueOnce({ id: 'doc-uploaded-1' });
    onboardingService.updateTask.mockResolvedValueOnce({ id: 'task-doc-1', status: 'SUBMITTED' });

    render(
      <MemoryRouter>
        <MyOnboarding />
      </MemoryRouter>
    );

    expect(await screen.findByText('Submit Passport Copy')).toBeDefined();

    const file = new File(['dummy content'], 'passport.pdf', { type: 'application/pdf' });
    const fileInput = document.querySelector('input[type="file"]');
    expect(fileInput).toBeDefined();

    fireEvent.change(fileInput, { target: { files: [file] } });

    await waitFor(() => {
      expect(documentService.uploadForEmployee).toHaveBeenCalledWith(
        file,
        'emp-me',
        'ID_PROOF'
      );
      expect(onboardingService.updateTask).toHaveBeenCalledWith(
        'emp-me',
        'task-doc-1',
        expect.objectContaining({
          status: 'SUBMITTED',
          documentId: 'doc-uploaded-1',
        })
      );
    });
  });

  it('allows acknowledging a policy with popconfirm', async () => {
    onboardingService.getMyProgress.mockResolvedValue({
      totalTasks: 1,
      completedTasks: 0,
      pendingTasks: 1,
      progressPercentage: 0,
      tasks: [
        {
          id: 'task-policy-1',
          employeeId: 'emp-me',
          title: 'Code of Conduct Handbook',
          taskType: 'POLICY_ACKNOWLEDGEMENT',
          assignedRole: 'EMPLOYEE',
          isMandatory: true,
          status: 'PENDING',
        },
      ],
    });
    onboardingService.updateTask.mockResolvedValueOnce({ id: 'task-policy-1', status: 'COMPLETED' });

    render(
      <MemoryRouter>
        <MyOnboarding />
      </MemoryRouter>
    );

    expect(await screen.findByText('Code of Conduct Handbook')).toBeDefined();
    const ackBtn = screen.getByRole('button', { name: /acknowledge/i });
    fireEvent.click(ackBtn);

    const agreeBtn = await screen.findByRole('button', { name: /i agree/i });
    fireEvent.click(agreeBtn);

    await waitFor(() => {
      expect(onboardingService.updateTask).toHaveBeenCalledWith(
        'emp-me',
        'task-policy-1',
        expect.objectContaining({ status: 'COMPLETED' })
      );
    });
  });
});
