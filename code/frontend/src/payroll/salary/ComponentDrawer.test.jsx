import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { ComponentDrawer } from './ComponentDrawer.jsx';
import { componentService } from './componentService.js';
import salaryReducer from './salarySlice.js';

vi.mock('./componentService.js', () => ({
  componentService: {
    create: vi.fn(),
    update: vi.fn(),
  },
}));

vi.mock('@shared/ui/msgHelper.js', () => ({
  successMsg: vi.fn(),
  errorMsg: vi.fn(),
}));

function renderDrawer(props = {}) {
  const store = configureStore({
    reducer: {
      salary: salaryReducer,
    },
  });

  const defaultProps = {
    open: true,
    onClose: vi.fn(),
    onSuccess: vi.fn(),
    kind: 'earnings',
    initialValues: null,
  };

  return render(
    <Provider store={store}>
      <ComponentDrawer {...defaultProps} {...props} />
    </Provider>
  );
}

describe('ComponentDrawer component (W-47.1a §7)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('percentageOf is hidden when calculationType is FLAT', async () => {
    renderDrawer({
      initialValues: {
        calculationType: 'FLAT',
      },
    });

    expect(screen.queryByText('Percentage Of')).toBeNull();
  });

  it('percentageOf is shown when calculationType is PERCENTAGE', async () => {
    renderDrawer({
      initialValues: {
        calculationType: 'PERCENTAGE',
      },
    });

    expect(screen.getByText('Percentage Of')).toBeDefined();
  });

  it('"12.5" is sent as a string on valid form submission', async () => {
    componentService.create.mockResolvedValueOnce({ id: 'comp-1' });

    renderDrawer({
      initialValues: null,
    });

    // Fill form using exact placeholder attributes
    fireEvent.change(screen.getByPlaceholderText('e.g. BASIC'), { target: { value: 'HRA' } });
    fireEvent.change(screen.getByPlaceholderText('e.g. Basic Salary'), { target: { value: 'HRA' } });
    fireEvent.change(screen.getByPlaceholderText('0.0000'), { target: { value: '12.5' } });

    // Submit using button id
    const saveBtn = document.getElementById('btn-save-component');
    expect(saveBtn).toBeTruthy();
    fireEvent.click(saveBtn);

    await waitFor(() => {
      expect(componentService.create).toHaveBeenCalled();
      const callPayload = componentService.create.mock.calls[0][1];
      expect(callPayload.defaultValue).toBe('12.5');
      expect(typeof callPayload.defaultValue).toBe('string');
    });
  });

  it('"1.23456" is blocked by decimal validation (> 4 decimal places)', async () => {
    renderDrawer({
      initialValues: null,
    });

    fireEvent.change(screen.getByPlaceholderText('e.g. BASIC'), { target: { value: 'HRA' } });
    fireEvent.change(screen.getByPlaceholderText('e.g. Basic Salary'), { target: { value: 'HRA' } });
    fireEvent.change(screen.getByPlaceholderText('0.0000'), { target: { value: '1.23456' } });

    const saveBtn = document.getElementById('btn-save-component');
    fireEvent.click(saveBtn);

    await waitFor(() => {
      expect(
        screen.getByText(/must be a valid number with at most 4 decimal places/i)
      ).toBeDefined();
      expect(componentService.create).not.toHaveBeenCalled();
    });
  });
});
