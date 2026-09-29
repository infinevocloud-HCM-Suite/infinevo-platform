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
    it('header calls GET me tax-declaration endpoint', async () => {
      const mockData = { financial_year: fy, regime: 'NEW' };
      apiClient.get.mockResolvedValueOnce({ data: mockData });

      const res = await declarationService.header(fy);
      expect(apiClient.get).toHaveBeenCalledWith('/api/v1/me/tax-declaration/2026-27');
      expect(res).toEqual(mockData);
    });

    it('saveHeader calls PUT me tax-declaration endpoint', async () => {
      const body = { regime: 'OLD', is_staying_in_rented_house: true };
      apiClient.put.mockResolvedValueOnce({ data: { success: true } });

      const res = await declarationService.saveHeader(fy, body);
      expect(apiClient.put).toHaveBeenCalledWith('/api/v1/me/tax-declaration/2026-27', body);
      expect(res).toEqual({ success: true });
    });

    it('submit calls POST me tax-declaration submit endpoint', async () => {
      apiClient.post.mockResolvedValueOnce({ data: { status: 'SUBMITTED' } });

      const res = await declarationService.submit(fy);
      expect(apiClient.post).toHaveBeenCalledWith('/api/v1/me/tax-declaration/2026-27/submit');
      expect(res).toEqual({ status: 'SUBMITTED' });
    });

    it('reopen calls POST me tax-declaration reopen endpoint', async () => {
      apiClient.post.mockResolvedValueOnce({ data: { status: 'DRAFT' } });

      const res = await declarationService.reopen(fy);
      expect(apiClient.post).toHaveBeenCalledWith('/api/v1/me/tax-declaration/2026-27/reopen');
      expect(res).toEqual({ status: 'DRAFT' });
    });

    it('headerOf calls GET officer tax-declaration endpoint for specific employee', async () => {
      const mockData = { employee_id: 'emp-123', financial_year: fy };
      apiClient.get.mockResolvedValueOnce({ data: mockData });

      const res = await declarationService.headerOf('emp-123', fy);
      expect(apiClient.get).toHaveBeenCalledWith(
        '/api/v1/payroll/employees/emp-123/tax-declaration/2026-27',
      );
      expect(res).toEqual(mockData);
    });
  });

  describe('Housing Section', () => {
    it('housing calls GET housing endpoint', async () => {
      const mockData = { house_rent: [], home_loans: [], let_out_properties: [] };
      apiClient.get.mockResolvedValueOnce({ data: mockData });

      const res = await declarationService.housing(fy);
      expect(apiClient.get).toHaveBeenCalledWith('/api/v1/me/tax-declaration/2026-27/housing');
      expect(res).toEqual(mockData);
    });

    it('saveHouseRent calls PUT house-rent endpoint', async () => {
      const body = { lines: [{ month: 4, rent_amount: 15000 }] };
      apiClient.put.mockResolvedValueOnce({ data: { ok: true } });

      const res = await declarationService.saveHouseRent(fy, body);
      expect(apiClient.put).toHaveBeenCalledWith(
        '/api/v1/me/tax-declaration/2026-27/house-rent',
        body,
      );
      expect(res).toEqual({ ok: true });
    });

    it('saveHomeLoan calls PUT home-loan endpoint', async () => {
      const body = { lender_name: 'HDFC Bank', interest_amount: 150000 };
      apiClient.put.mockResolvedValueOnce({ data: { ok: true } });

      const res = await declarationService.saveHomeLoan(fy, body);
      expect(apiClient.put).toHaveBeenCalledWith(
        '/api/v1/me/tax-declaration/2026-27/home-loan',
        body,
      );
      expect(res).toEqual({ ok: true });
    });

    it('saveLetOut calls PUT let-out-property endpoint', async () => {
      const body = { gross_rent_received: 120000 };
      apiClient.put.mockResolvedValueOnce({ data: { ok: true } });

      const res = await declarationService.saveLetOut(fy, body);
      expect(apiClient.put).toHaveBeenCalledWith(
        '/api/v1/me/tax-declaration/2026-27/let-out-property',
        body,
      );
      expect(res).toEqual({ ok: true });
    });
  });

  describe('Deductions Section', () => {
    it('items calls GET section6a-items catalogue', async () => {
      const mockItems = [{ code: '80C', name: 'Life Insurance', max_limit: 150000 }];
      apiClient.get.mockResolvedValueOnce({ data: mockItems });

      const res = await declarationService.items(fy);
      expect(apiClient.get).toHaveBeenCalledWith(
        '/api/v1/me/tax-declaration/2026-27/section6a-items',
      );
      expect(res).toEqual(mockItems);
    });

    it('deductions calls GET deductions endpoint', async () => {
      const mockData = { section6a: [], pre_tax_deductions: [], previous_employment: null };
      apiClient.get.mockResolvedValueOnce({ data: mockData });

      const res = await declarationService.deductions(fy);
      expect(apiClient.get).toHaveBeenCalledWith('/api/v1/me/tax-declaration/2026-27/deductions');
      expect(res).toEqual(mockData);
    });

    it('save6a calls PUT section6a endpoint', async () => {
      const body = { items: [{ item_code: '80C', declared_amount: 50000 }] };
      apiClient.put.mockResolvedValueOnce({ data: { ok: true } });

      const res = await declarationService.save6a(fy, body);
      expect(apiClient.put).toHaveBeenCalledWith(
        '/api/v1/me/tax-declaration/2026-27/section6a',
        body,
      );
      expect(res).toEqual({ ok: true });
    });

    it('savePreTax calls PUT pre-tax-deductions endpoint', async () => {
      const body = { items: [{ deduction_code: 'NPS', declared_amount: 50000 }] };
      apiClient.put.mockResolvedValueOnce({ data: { ok: true } });

      const res = await declarationService.savePreTax(fy, body);
      expect(apiClient.put).toHaveBeenCalledWith(
        '/api/v1/me/tax-declaration/2026-27/pre-tax-deductions',
        body,
      );
      expect(res).toEqual({ ok: true });
    });

    it('savePrevEmployment calls PUT previous-employment endpoint', async () => {
      const body = { previous_employer_name: 'Acme Corp', gross_salary: 300000 };
      apiClient.put.mockResolvedValueOnce({ data: { ok: true } });

      const res = await declarationService.savePrevEmployment(fy, body);
      expect(apiClient.put).toHaveBeenCalledWith(
        '/api/v1/me/tax-declaration/2026-27/previous-employment',
        body,
      );
      expect(res).toEqual({ ok: true });
    });
  });

  describe('Other Income Section', () => {
    it('otherIncome calls GET other-income endpoint', async () => {
      const mockData = { items: [{ income_source: 'SAVINGS_INTEREST', amount: 5000 }] };
      apiClient.get.mockResolvedValueOnce({ data: mockData });

      const res = await declarationService.otherIncome(fy);
      expect(apiClient.get).toHaveBeenCalledWith('/api/v1/me/tax-declaration/2026-27/other-income');
      expect(res).toEqual(mockData);
    });

    it('saveOtherIncome calls PUT other-income endpoint', async () => {
      const body = { items: [{ income_source: 'SAVINGS_INTEREST', amount: 8000 }] };
      apiClient.put.mockResolvedValueOnce({ data: { ok: true } });

      const res = await declarationService.saveOtherIncome(fy, body);
      expect(apiClient.put).toHaveBeenCalledWith(
        '/api/v1/me/tax-declaration/2026-27/other-income',
        body,
      );
      expect(res).toEqual({ ok: true });
    });
  });

  describe('Summary Section', () => {
    it('summary calls GET summary endpoint', async () => {
      const mockData = { declared: { section6a_total: 150000 }, computed: {} };
      apiClient.get.mockResolvedValueOnce({ data: mockData });

      const res = await declarationService.summary(fy);
      expect(apiClient.get).toHaveBeenCalledWith('/api/v1/me/tax-declaration/2026-27/summary');
      expect(res).toEqual(mockData);
    });
  });
});
