import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { PlatformBillingTab } from './PlatformBillingTab.jsx';
import { billingService } from '../billing/billingService.js';
import * as screens from '@shell/screens';

vi.mock('../billing/billingService.js', () => ({
  billingService: {
    listInvoices: vi.fn(),
    downloadInvoicePdf: vi.fn(),
    recordPayment: vi.fn(),
    generateInvoice: vi.fn(),
  },
}));

const mockTenant = {
  tenant_id: 'tenant-acme',
  name: 'Acme Corp',
  plan: 'GROWTH',
  country_code: 'IN',
};

const mockInvoices = [
  {
    id: 'inv-101',
    tenantId: 'tenant-acme',
    invoiceNumber: 'INF/2026-27/0101',
    invoiceDate: '2026-04-01',
    dueDate: '2026-04-15',
    billingPeriodStart: '2026-04-01',
    billingPeriodEnd: '2026-04-30',
    billingFrequency: 'MONTHLY',
    taxableAmount: 1975,
    totalTax: 355.5,
    totalAmount: 2330.5,
    amountPaid: 0,
    status: 'ISSUED',
  },
  {
    id: 'inv-102',
    tenantId: 'tenant-acme',
    invoiceNumber: 'INF/2026-27/0102',
    invoiceDate: '2026-03-01',
    dueDate: '2026-03-15',
    billingPeriodStart: '2026-03-01',
    billingPeriodEnd: '2026-03-31',
    billingFrequency: 'MONTHLY',
    taxableAmount: 1975,
    totalTax: 355.5,
    totalAmount: 2330.5,
    amountPaid: 2330.5,
    status: 'PAID',
  },
];

function setup({ canManage = true, invoices = mockInvoices } = {}) {
  vi.spyOn(screens, 'useCan').mockImplementation((code) => {
    if (code === 'core.billing.manage') return canManage;
    return false;
  });

  billingService.listInvoices.mockResolvedValue(invoices);
  billingService.downloadInvoicePdf.mockResolvedValue(new Blob(['pdf']));

  return render(
    <PlatformBillingTab tenant={mockTenant} onUpdate={vi.fn()} />
  );
}

describe('PlatformBillingTab', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders summary statistics and tenant invoices table', async () => {
    setup();

    expect(await screen.findByText('Tenant Invoices')).toBeDefined();
    expect(screen.getByText('Invoices Issued')).toBeDefined();
    expect(screen.getByText('Total Billed')).toBeDefined();
    expect(screen.getByText('Total Collected')).toBeDefined();
    expect(screen.getByText('Outstanding Balance')).toBeDefined();

    // Table rows
    expect(screen.getByText('INF/2026-27/0101')).toBeDefined();
    expect(screen.getByText('INF/2026-27/0102')).toBeDefined();

    // Status tags
    expect(screen.getByText('Issued')).toBeDefined();
    expect(screen.getByText('Paid')).toBeDefined();
  });

  it('opens Record Payment modal when Record Payment is clicked on unpaid invoice', async () => {
    setup();

    const recordPaymentButtons = await screen.findAllByRole('button', { name: /Record Payment/i });
    // First invoice is ISSUED, so button is enabled
    fireEvent.click(recordPaymentButtons[0]);

    expect(await screen.findByText(/Record Payment for INF\/2026-27\/0101/)).toBeDefined();
  });

  it('opens Generate Invoice modal when Generate Invoice button is clicked', async () => {
    setup();

    const generateBtn = await screen.findByRole('button', { name: /Generate Invoice/i });
    fireEvent.click(generateBtn);

    expect(await screen.findByText(/Generate Invoice for Acme Corp/)).toBeDefined();
  });

  it('shows warning alert when lacking core.billing.manage', async () => {
    setup({ canManage: false });

    expect(
      await screen.findByText(/Read-only view\. Generating invoices and recording payments requires core\.billing\.manage/i)
    ).toBeDefined();
    expect(screen.queryByRole('button', { name: /Generate Invoice/i })).toBeNull();
  });
});
