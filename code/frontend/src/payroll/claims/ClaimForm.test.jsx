import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import dayjs from 'dayjs';
import { ClaimForm, claimSchema } from './ClaimForm.jsx';
import { claimService } from './claimService.js';

vi.mock('./claimService.js', () => ({
  claimService: {
    components: vi.fn(),
    submit: vi.fn(),
  },
}));

const COMPONENTS = [
  { id: 'c-fuel', code: 'FUEL', name: 'Fuel', max_limit: 2000 },
  { id: 'c-phone', code: 'PHONE', name: 'Phone', max_limit: null },
];

async function renderForm(props = {}) {
  const onClose = vi.fn();
  const onSubmitted = vi.fn();
  render(<ClaimForm open onClose={onClose} onSubmitted={onSubmitted} {...props} />);
  await waitFor(() => expect(claimService.components).toHaveBeenCalled());
  return { onClose, onSubmitted };
}

async function chooseComponent(name) {
  fireEvent.mouseDown(screen.getByRole('combobox', { name: 'Component' }));
  fireEvent.click(await screen.findByText(name, { selector: '.ant-select-item-option-content' }));
}

function typeAmount(value) {
  const input = screen.getByRole('spinbutton', { name: 'Amount' });
  fireEvent.change(input, { target: { value } });
  fireEvent.blur(input);
}

function typeBillDate(value) {
  const input = screen.getByPlaceholderText('Select bill date');
  fireEvent.mouseDown(input);
  fireEvent.change(input, { target: { value } });
  fireEvent.keyDown(input, { key: 'Enter', code: 'Enter' });
}

const submitButton = () => screen.getByRole('button', { name: /submit claim/i });

describe('ClaimForm (W-47.4 §7)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    claimService.components.mockResolvedValue(COMPONENTS);
  });

  it('warns above max_limit and still submits the amount as a two-decimal string', async () => {
    claimService.submit.mockResolvedValueOnce({ id: 'claim-1', status: 'SUBMITTED' });
    const { onSubmitted, onClose } = await renderForm();

    await chooseComponent('Fuel');
    typeAmount('2500');
    expect(await screen.findByTestId('over-limit')).toBeDefined();
    expect(screen.getByText(/Above the limit of 2,000.00/)).toBeDefined();

    const yesterday = dayjs().subtract(1, 'day').format('YYYY-MM-DD');
    typeBillDate(yesterday);
    fireEvent.click(submitButton());

    await waitFor(() =>
      expect(claimService.submit).toHaveBeenCalledWith({
        reimbursement_id: 'c-fuel',
        requested_amount: '2500.00',
        bill_date: yesterday,
        description: null,
        document_id: null,
      })
    );
    await waitFor(() => expect(onSubmitted).toHaveBeenCalledWith({ id: 'claim-1', status: 'SUBMITTED' }));
    expect(onClose).toHaveBeenCalled();
  });

  it('shows no warning at or under the limit', async () => {
    await renderForm();
    await chooseComponent('Fuel');
    typeAmount('2000');
    await waitFor(() => expect(screen.queryByTestId('over-limit')).toBeNull());
  });

  it('refuses a future bill date', async () => {
    await renderForm();
    await chooseComponent('Fuel');
    typeAmount('100');
    typeBillDate(dayjs().add(5, 'day').format('YYYY-MM-DD'));
    fireEvent.click(submitButton());

    await waitFor(() => {
      // A disabled (future) day is not taken by the picker, so the field stays empty and refused.
      const help = screen.getByText(/bill date/i, { selector: '.ant-form-item-explain-error' });
      expect(help).toBeDefined();
      expect(screen.getByPlaceholderText('Select bill date').value).toBe('');
    });
    expect(claimService.submit).not.toHaveBeenCalled();
  });

  it('the schema refuses a future bill date even if one reaches it', async () => {
    const base = { reimbursement_id: 'c-fuel', requested_amount: '100', description: '' };
    await expect(
      claimSchema.validateAt('bill_date', { ...base, bill_date: dayjs().add(1, 'day') })
    ).rejects.toThrow('The bill date cannot be in the future');
    await expect(claimSchema.validateAt('bill_date', { ...base, bill_date: dayjs() })).resolves.toBeTruthy();
  });

  it('puts a 400 fieldErrors on the fields', async () => {
    claimService.submit.mockRejectedValueOnce({
      status: 400,
      code: 'VALIDATION_FAILED',
      message: 'The claim was refused',
      fieldErrors: { requested_amount: 'Amount exceeds what this component allows', billDate: 'Bill date too old' },
    });
    const { onSubmitted } = await renderForm();
    await chooseComponent('Phone');
    typeAmount('100');
    typeBillDate(dayjs().subtract(1, 'day').format('YYYY-MM-DD'));
    fireEvent.click(submitButton());

    expect(await screen.findByText('Amount exceeds what this component allows')).toBeDefined();
    expect(screen.getByText('Bill date too old')).toBeDefined();
    expect(screen.getByText('The claim was refused')).toBeDefined();
    expect(onSubmitted).not.toHaveBeenCalled();
  });

  it('posts once on a double click and disables submit while posting', async () => {
    let resolve;
    claimService.submit.mockImplementation(
      () =>
        new Promise((r) => {
          resolve = r;
        })
    );
    await renderForm();
    await chooseComponent('Phone');
    typeAmount('100');
    typeBillDate(dayjs().subtract(1, 'day').format('YYYY-MM-DD'));

    fireEvent.click(submitButton());
    fireEvent.click(submitButton());

    await waitFor(() => expect(claimService.submit).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(submitButton().disabled).toBe(true));
    fireEvent.click(submitButton());
    expect(claimService.submit).toHaveBeenCalledTimes(1);

    resolve({ id: 'claim-2' });
    await waitFor(() => expect(claimService.submit).toHaveBeenCalledTimes(1));
  });

  it('shows Retry and no form when the components cannot be loaded', async () => {
    claimService.components.mockRejectedValueOnce({ status: 500, message: 'Server down' });
    await renderForm();

    expect(await screen.findByText('Server down')).toBeDefined();
    expect(screen.queryByRole('combobox', { name: 'Component' })).toBeNull();

    claimService.components.mockResolvedValueOnce(COMPONENTS);
    fireEvent.click(screen.getByRole('button', { name: /retry/i }));
    expect(await screen.findByRole('combobox', { name: 'Component' })).toBeDefined();
  });
});
