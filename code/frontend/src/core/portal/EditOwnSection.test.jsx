import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { EditOwnSection } from './EditOwnSection.jsx';
import { useCan } from '@shell/screens';
import { portalService } from '@shell/portal/portalService.js';
import { employeeService } from '../employee/employeeService.js';

vi.mock('@shell/screens', () => ({
  useCan: vi.fn(),
}));

vi.mock('@shell/portal/portalService.js', () => ({
  portalService: {
    getProfile: vi.fn(),
  },
}));

vi.mock('../employee/employeeService.js', () => ({
  employeeService: {
    section: vi.fn(),
    saveSection: vi.fn(),
  },
}));

vi.mock('@shared/ui/msgHelper.js', () => ({
  successMsg: vi.fn().mockResolvedValue(true),
  errorMsg: vi.fn().mockResolvedValue(true),
}));

describe('EditOwnSection component (W-46.5 §5, §7)', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    useCan.mockReset();
    portalService.getProfile.mockReset();
    employeeService.section.mockReset();
    employeeService.saveSection.mockReset();
  });

  it('renders null when caller lacks core.employee.update_own permission', () => {
    useCan.mockReturnValue(false);

    const { container } = render(<EditOwnSection employeeId="emp-123" />);
    expect(container.firstChild).toBeNull();
    expect(useCan).toHaveBeenCalledWith('core.employee.update_own');
  });

  it('renders personal and contact tabs and saves personal section through saveSection', async () => {
    useCan.mockReturnValue(true);
    portalService.getProfile.mockResolvedValue({ id: 'emp-me-456' });
    employeeService.section.mockResolvedValue({
      maritalStatus: 'Single',
      fatherName: 'Old Father',
    });
    employeeService.saveSection.mockResolvedValue({});

    render(<EditOwnSection />);

    await waitFor(() => {
      expect(screen.getByTestId('edit-own-section')).toBeDefined();
      expect(portalService.getProfile).toHaveBeenCalled();
    });

    expect(screen.getByText('Personal Details')).toBeDefined();
    expect(screen.getByText('Contact Details')).toBeDefined();

    // Verify only personal and contact sections exist
    expect(screen.queryByText(/identification/i)).toBeNull();
    expect(screen.queryByText(/employment/i)).toBeNull();
    expect(screen.queryByText(/bank/i)).toBeNull();

    await waitFor(() => {
      expect(employeeService.section).toHaveBeenCalledWith('emp-me-456', 'personal');
      expect(screen.getByDisplayValue('Old Father')).toBeDefined();
    });

    const fatherInput = screen.getByDisplayValue('Old Father');
    fireEvent.change(fatherInput, { target: { value: 'Updated Father Name' } });

    const saveBtn = screen.getByRole('button', { name: /Save Personal Details/i });
    fireEvent.click(saveBtn);

    await waitFor(() => {
      expect(employeeService.saveSection).toHaveBeenCalledWith(
        'emp-me-456',
        'personal',
        expect.objectContaining({
          fatherName: 'Updated Father Name',
        })
      );
    });
  });

  it('switches to contact tab and saves contact section through saveSection', async () => {
    useCan.mockReturnValue(true);
    employeeService.section.mockImplementation((empId, section) => {
      if (section === 'personal') return Promise.resolve({});
      if (section === 'contact') {
        return Promise.resolve({
          personalEmail: 'me@example.com',
          city: 'Mumbai',
        });
      }
      return Promise.resolve({});
    });
    employeeService.saveSection.mockResolvedValue({});

    render(<EditOwnSection employeeId="emp-direct-789" />);

    await waitFor(() => {
      expect(screen.getByTestId('edit-own-section')).toBeDefined();
      expect(portalService.getProfile).not.toHaveBeenCalled();
    });

    // Switch to Contact tab
    const contactTab = screen.getByText('Contact Details');
    fireEvent.click(contactTab);

    await waitFor(() => {
      expect(employeeService.section).toHaveBeenCalledWith('emp-direct-789', 'contact');
      expect(screen.getByDisplayValue('me@example.com')).toBeDefined();
    });

    const emailInput = screen.getByDisplayValue('me@example.com');
    fireEvent.change(emailInput, { target: { value: 'updated@example.com' } });

    const saveBtn = screen.getByRole('button', { name: /Save Contact Details/i });
    fireEvent.click(saveBtn);

    await waitFor(() => {
      expect(employeeService.saveSection).toHaveBeenCalledWith(
        'emp-direct-789',
        'contact',
        expect.objectContaining({
          personalEmail: 'updated@example.com',
        })
      );
    });
  });
});
