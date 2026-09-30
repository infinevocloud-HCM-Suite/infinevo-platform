import { describe, it, expect, vi, beforeEach } from 'vitest';
import { declarationService } from './declarationService';
import { apiClient } from '@shared/api/client';

vi.mock('@shared/api/client', () => ({
  apiClient: {
    get: vi.fn(),
    put: vi.fn(),
    post: vi.fn(),
  },
}));

describe('declarationService', () => {
  const fy = '2026-27';

  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('Header & Lifecycle', () => {
    it('header calls GET me tax-declaration endpoint and unwraps ApiResponse', async () => {
      const mockData = { financial_year: fy, tax_regime: 'NEW' };
      apiClient.get.mockResolvedValueOnce({ data: { data: mockData } });

      const res = await declarationService.header(fy);
      expect(apiClient.get).toHaveBeenCalledWith('/api/v1/me/tax-declaration/2026-27');
      expect(res).toEqual(mockData);
    });

    it('saveHeader calls PUT me tax-declaration endpoint and unwraps ApiResponse', async () => {
      const body = { tax_regime: 'OLD', is_staying_in_rented_house: true };
      apiClient.put.mockResolvedValueOnce({ data: { data: { success: true } } });

      const res = await declarationService.saveHeader(fy, body);
      expect(apiClient.put).toHaveBeenCalledWith('/api/v1/me/tax-declaration/2026-27', body);
      expect(res).toEqual({ success: true });
    });

    it('submit calls POST me tax-declaration submit endpoint and unwraps ApiResponse', async () => {
      apiClient.post.mockResolvedValueOnce({ data: { data: { status: 'SUBMITTED' } } });

      const res = await declarationService.submit(fy);
      expect(apiClient.post).toHaveBeenCalledWith('/api/v1/me/tax-declaration/2026-27/submit');
      expect(res).toEqual({ status: 'SUBMITTED' });
    });

    it('reopen calls POST me tax-declaration reopen endpoint and unwraps ApiResponse', async () => {
      apiClient.post.mockResolvedValueOnce({ data: { data: { status: 'DRAFT' } } });

      const res = await declarationService.reopen(fy);
      expect(apiClient.post).toHaveBeenCalledWith('/api/v1/me/tax-declaration/2026-27/reopen');
      expect(res).toEqual({ status: 'DRAFT' });
    });

    it('headerOf calls GET officer tax-declaration endpoint for specific employee and unwraps ApiResponse', async () => {
      const mockData = { employee_id: 'emp-123', financial_year: fy, tax_regime: 'NEW' };
      apiClient.get.mockResolvedValueOnce({ data: { data: mockData } });

      const res = await declarationService.headerOf('emp-123', fy);
      expect(apiClient.get).toHaveBeenCalledWith(
        '/api/v1/payroll/employees/emp-123/tax-declaration/2026-27',
      );
      expect(res).toEqual(mockData);
    });
  });

  describe('Housing Section', () => {
    it('housing calls GET housing endpoint and unwraps ApiResponse', async () => {
      const mockData = { house_rent: [], home_loans: [], let_out_properties: [] };
      apiClient.get.mockResolvedValueOnce({ data: { data: mockData } });

      const res = await declarationService.housing(fy);
      expect(apiClient.get).toHaveBeenCalledWith('/api/v1/me/tax-declaration/2026-27/housing');
      expect(res).toEqual(mockData);
    });

    it('saveHouseRent calls PUT house-rent endpoint and unwraps ApiResponse', async () => {
      const body = [{ from_month: '2026-04', to_month: '2027-03', amount_per_month: 15000 }];
      apiClient.put.mockResolvedValueOnce({ data: { data: { ok: true } } });

      const res = await declarationService.saveHouseRent(fy, body);
      expect(apiClient.put).toHaveBeenCalledWith(
        '/api/v1/me/tax-declaration/2026-27/house-rent',
        body,
      );
      expect(res).toEqual({ ok: true });
    });

    it('saveHomeLoan calls PUT home-loan endpoint and unwraps ApiResponse', async () => {
      const body = [{ lender_name: 'HDFC Bank', interest_paid: 150000 }];
      apiClient.put.mockResolvedValueOnce({ data: { data: { ok: true } } });

      const res = await declarationService.saveHomeLoan(fy, body);
      expect(apiClient.put).toHaveBeenCalledWith(
        '/api/v1/me/tax-declaration/2026-27/home-loan',
        body,
      );
      expect(res).toEqual({ ok: true });
    });

    it('saveLetOut calls PUT let-out-property endpoint and unwraps ApiResponse', async () => {
      const body = [{ property_name: 'Property #1', lines: [] }];
      apiClient.put.mockResolvedValueOnce({ data: { data: { ok: true } } });

      const res = await declarationService.saveLetOut(fy, body);
      expect(apiClient.put).toHaveBeenCalledWith(
        '/api/v1/me/tax-declaration/2026-27/let-out-property',
        body,
      );
      expect(res).toEqual({ ok: true });
    });
  });

  describe('Deductions Section', () => {
    it('items calls GET section6a-items catalogue and unwraps ApiResponse', async () => {
      const mockItems = [{ section_code: '80C_LIC', category_group_code: '80C', name: 'Life Insurance', max_limit: 150000 }];
      apiClient.get.mockResolvedValueOnce({ data: { data: mockItems } });

      const res = await declarationService.items(fy);
      expect(apiClient.get).toHaveBeenCalledWith(
        '/api/v1/me/tax-declaration/2026-27/section6a-items',
      );
      expect(res).toEqual(mockItems);
    });

    it('deductions calls GET deductions endpoint and unwraps ApiResponse', async () => {
      const mockData = { section6a: [], pre_tax_deductions: [], previous_employment: [] };
      apiClient.get.mockResolvedValueOnce({ data: { data: mockData } });

      const res = await declarationService.deductions(fy);
      expect(apiClient.get).toHaveBeenCalledWith('/api/v1/me/tax-declaration/2026-27/deductions');
      expect(res).toEqual(mockData);
    });

    it('save6a calls PUT section6a endpoint and unwraps ApiResponse', async () => {
      const body = [{ section6a_item_id: 'item-1', amount: 50000 }];
      apiClient.put.mockResolvedValueOnce({ data: { data: { ok: true } } });

      const res = await declarationService.save6a(fy, body);
      expect(apiClient.put).toHaveBeenCalledWith(
        '/api/v1/me/tax-declaration/2026-27/section6a',
        body,
      );
      expect(res).toEqual({ ok: true });
    });

    it('savePreTax calls PUT pre-tax-deductions endpoint and unwraps ApiResponse', async () => {
      const body = [{ kind: 'NPS_EMPLOYEE', amount: 50000 }];
      apiClient.put.mockResolvedValueOnce({ data: { data: { ok: true } } });

      const res = await declarationService.savePreTax(fy, body);
      expect(apiClient.put).toHaveBeenCalledWith(
        '/api/v1/me/tax-declaration/2026-27/pre-tax-deductions',
        body,
      );
      expect(res).toEqual({ ok: true });
    });

    it('savePrevEmployment calls PUT previous-employment endpoint and unwraps ApiResponse', async () => {
      const body = [{ kind: 'INCOME', amount: 300000, employer_name: 'Acme Corp' }];
      apiClient.put.mockResolvedValueOnce({ data: { data: { ok: true } } });

      const res = await declarationService.savePrevEmployment(fy, body);
      expect(apiClient.put).toHaveBeenCalledWith(
        '/api/v1/me/tax-declaration/2026-27/previous-employment',
        body,
      );
      expect(res).toEqual({ ok: true });
    });
  });

  describe('Other Income Section', () => {
    it('otherIncome calls GET other-income endpoint and unwraps ApiResponse', async () => {
      const mockData = [{ kind: 'SAVINGS_INTEREST', amount: 5000 }];
      apiClient.get.mockResolvedValueOnce({ data: { data: mockData } });

      const res = await declarationService.otherIncome(fy);
      expect(apiClient.get).toHaveBeenCalledWith('/api/v1/me/tax-declaration/2026-27/other-income');
      expect(res).toEqual(mockData);
    });

    it('saveOtherIncome calls PUT other-income endpoint and unwraps ApiResponse', async () => {
      const body = [{ kind: 'SAVINGS_INTEREST', amount: 8000 }];
      apiClient.put.mockResolvedValueOnce({ data: { data: { ok: true } } });

      const res = await declarationService.saveOtherIncome(fy, body);
      expect(apiClient.put).toHaveBeenCalledWith(
        '/api/v1/me/tax-declaration/2026-27/other-income',
        body,
      );
      expect(res).toEqual({ ok: true });
    });
  });

  describe('Summary Section', () => {
    it('summary calls GET summary endpoint and unwraps ApiResponse', async () => {
      const mockData = { declared: { section6a_total: 150000 }, computed: {} };
      apiClient.get.mockResolvedValueOnce({ data: { data: mockData } });

      const res = await declarationService.summary(fy);
      expect(apiClient.get).toHaveBeenCalledWith('/api/v1/me/tax-declaration/2026-27/summary');
      expect(res).toEqual(mockData);
    });
  });
});
