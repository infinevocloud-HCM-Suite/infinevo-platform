import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import * as useCanModule from '@shell/screens';
import { TdsDeductorSettings, toDeductorRequest } from './TdsDeductorSettings.jsx';
import { form24QService } from '../form24q/form24QService.js';

vi.mock('../form24q/form24QService.js', () => ({
  form24QService: { getDeductor: vi.fn(), saveDeductor: vi.fn() },
}));

const saved = {
  tan: 'BLRA12345B',
  pan: 'AAACA1234C',
  tds_circle: null,
  signatory_employee_id: 'emp-9',
  signatory_name: 'Ravi Menon',
  signatory_parent_name: null,
  signatory_designation: 'Director',
  deductor_type: 'K',
  email: 'tax@acme.in',
  phone: '8012345678',
  gstin: null,
  responsible_pan: 'ABCPM1234D',
  responsible_email: 'ravi@acme.in',
  responsible_phone: '9876543210',
};

describe('TdsDeductorSettings (W-89)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.spyOn(useCanModule, 'useCan').mockReturnValue(true);
  });

  it('builds the request: trims, empty is null, keeps the signatory employee', () => {
    const body = toDeductorRequest({ ...saved, email: '  tax@acme.in ', gstin: '' }, saved);
    expect(body.email).toBe('tax@acme.in');
    expect(body.gstin).toBeNull();
    expect(body.signatory_employee_id).toBe('emp-9');
  });

  it('loads the saved details and saves changes', async () => {
    form24QService.getDeductor.mockResolvedValue(saved);
    form24QService.saveDeductor.mockResolvedValue(saved);
    render(<TdsDeductorSettings />);
    await waitFor(() => expect(screen.getByLabelText('TAN').value).toBe('BLRA12345B'));
    fireEvent.change(screen.getByLabelText('Company phone'), { target: { value: '8099999999' } });
    fireEvent.click(screen.getByTestId('btn-save-deductor'));
    await waitFor(() => expect(form24QService.saveDeductor).toHaveBeenCalled());
    expect(form24QService.saveDeductor.mock.calls[0][0]).toMatchObject({ phone: '8099999999', tan: 'BLRA12345B' });
  });

  it('starts empty when nothing is saved, and a viewer cannot save', async () => {
    useCanModule.useCan.mockReturnValue(false);
    form24QService.getDeductor.mockResolvedValue(null);
    render(<TdsDeductorSettings />);
    await waitFor(() => expect(screen.getByLabelText('TAN')).toBeDefined());
    expect(screen.getByLabelText('TAN').value).toBe('');
    expect(screen.queryByTestId('btn-save-deductor')).toBeNull();
  });
});
