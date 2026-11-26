import { describe, it, expect, vi, beforeEach } from 'vitest';
import { billingService, generateInvoiceDocumentContent } from './billingService.js';
import { apiClient } from '@shared/api/client.js';

vi.mock('@shared/api/client.js', () => ({
  apiClient: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

describe('billingService', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('listInvoices calls GET /v1/core/billing/invoices with params', async () => {
    const mockInvoices = [{ id: 'inv-1', invoiceNumber: 'INF/2026-27/0001' }];
    apiClient.get.mockResolvedValueOnce(mockInvoices);

    const result = await billingService.listInvoices({ tenantId: 't-1' });
    expect(apiClient.get).toHaveBeenCalledWith('/v1/core/billing/invoices', { params: { tenantId: 't-1' } });
    expect(result).toEqual(mockInvoices);
  });

  it('getInvoice calls GET /v1/core/billing/invoices/{id}', async () => {
    const mockInvoice = { id: 'inv-1', invoiceNumber: 'INF/2026-27/0001' };
    apiClient.get.mockResolvedValueOnce({ data: mockInvoice });

    const result = await billingService.getInvoice('inv-1');
    expect(apiClient.get).toHaveBeenCalledWith('/v1/core/billing/invoices/inv-1');
    expect(result).toEqual(mockInvoice);
  });

  it('generateInvoice calls POST /v1/core/billing/invoices/generate with skipImpersonation', async () => {
    const payload = {
      tenantId: 't-1',
      billingPeriodStart: '2026-04-01',
      billingPeriodEnd: '2026-04-30',
      billingFrequency: 'MONTHLY',
    };
    const created = { id: 'inv-new', invoiceNumber: 'INF/2026-27/0002' };
    apiClient.post.mockResolvedValueOnce(created);

    const result = await billingService.generateInvoice(payload);
    expect(apiClient.post).toHaveBeenCalledWith(
      '/v1/core/billing/invoices/generate',
      payload,
      { skipImpersonation: true }
    );
    expect(result).toEqual(created);
  });

  it('recordPayment calls POST /v1/core/billing/invoices/{id}/payments with skipImpersonation', async () => {
    const payload = {
      paymentDate: '2026-04-05',
      amount: 4900,
      paymentMethod: 'NEFT',
      transactionReference: 'UTR12345678',
      notes: 'Monthly settlement',
    };
    const payment = { id: 'pay-1', status: 'SUCCESS' };
    apiClient.post.mockResolvedValueOnce(payment);

    const result = await billingService.recordPayment('inv-1', payload);
    expect(apiClient.post).toHaveBeenCalledWith(
      '/v1/core/billing/invoices/inv-1/payments',
      payload,
      { skipImpersonation: true }
    );
    expect(result).toEqual(payment);
  });

  it('listPayments calls GET /v1/core/billing/invoices/{id}/payments', async () => {
    const payments = [{ id: 'pay-1', amount: 4900 }];
    apiClient.get.mockResolvedValueOnce(payments);

    const result = await billingService.listPayments('inv-1');
    expect(apiClient.get).toHaveBeenCalledWith('/v1/core/billing/invoices/inv-1/payments');
    expect(result).toEqual(payments);
  });

  it('downloadInvoicePdf generates document and triggers download', async () => {
    const invoice = {
      invoiceNumber: 'INF/2026-27/0001',
      invoiceDate: '2026-04-01',
      dueDate: '2026-04-15',
      planTier: 'GROWTH',
      subtotal: 4900,
      taxableAmount: 4900,
      cgstAmount: 441,
      sgstAmount: 441,
      totalTax: 882,
      totalAmount: 5782,
      status: 'ISSUED',
    };

    const createObjectURL = vi.fn().mockReturnValue('blob:test-url');
    const revokeObjectURL = vi.fn();
    window.URL.createObjectURL = createObjectURL;
    window.URL.revokeObjectURL = revokeObjectURL;

    const appendChildSpy = vi.spyOn(document.body, 'appendChild');

    const blob = await billingService.downloadInvoicePdf(invoice);
    expect(blob).toBeInstanceOf(Blob);
    expect(createObjectURL).toHaveBeenCalled();
    expect(appendChildSpy).toHaveBeenCalled();
  });

  it('generateInvoiceDocumentContent builds structured text', () => {
    const invoice = {
      invoiceNumber: 'INF/2026-27/0001',
      invoiceDate: '2026-04-01',
      dueDate: '2026-04-15',
      planTier: 'STARTER',
      billingFrequency: 'MONTHLY',
      activeHeadcount: 15,
      subtotal: 490,
      discountAmount: 0,
      taxableAmount: 490,
      cgstAmount: 44.1,
      sgstAmount: 44.1,
      totalTax: 88.2,
      totalAmount: 578.2,
      amountPaid: 0,
      status: 'ISSUED',
    };
    const content = generateInvoiceDocumentContent(invoice);
    expect(content).toContain('TAX INVOICE');
    expect(content).toContain('INF/2026-27/0001');
    expect(content).toContain('STARTER (MONTHLY)');
    expect(content).toContain('15 active employees');
  });
});
