import { describe, it, expect, vi, beforeEach } from 'vitest';
import { publicPayslipService } from './publicPayslipService.js';
import { apiClient } from '@shared/api/client.js';

vi.mock('@shared/api/client.js', () => ({
  apiClient: {
    get: vi.fn(),
  },
}));

describe('publicPayslipService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('rejects with error if token is missing or empty', async () => {
    await expect(publicPayslipService.getPayslip('')).rejects.toThrow(
      'This link is not valid or has expired',
    );
    await expect(publicPayslipService.getPayslip(null)).rejects.toThrow(
      'This link is not valid or has expired',
    );
    await expect(publicPayslipService.getPayslip('   ')).rejects.toThrow(
      'This link is not valid or has expired',
    );
  });

  it('calls /v1/payroll/payslips/open with param t and unwraps data envelope', async () => {
    const mockPayslip = {
      run: { period: '2026-10', status: 'PAID' },
      employee: { name: 'Sahmad' },
      totals: { net_pay: 77240.0 },
    };

    apiClient.get.mockResolvedValueOnce({
      data: {
        status: 200,
        message: 'Payslip retrieved successfully',
        data: mockPayslip,
      },
    });

    const result = await publicPayslipService.getPayslip('sample-token-123');

    expect(apiClient.get).toHaveBeenCalledWith('/v1/payroll/payslips/open', {
      params: { t: 'sample-token-123' },
    });
    expect(result).toEqual(mockPayslip);
  });

  it('returns body directly if data property is absent', async () => {
    const directData = { run: { period: '2026-10' } };
    apiClient.get.mockResolvedValueOnce({ data: directData });

    const result = await publicPayslipService.getPayslip('token-456');
    expect(result).toEqual(directData);
  });
});
