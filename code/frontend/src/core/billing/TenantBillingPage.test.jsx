import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { TenantBillingPage } from './TenantBillingPage.jsx';
import { billingService } from './billingService.js';
import { employeeService } from '../employee/employeeService.js';
import { tenantService } from '../admin/tenantService.js';
import * as screens from '@shell/screens';

vi.mock('./billingService.js', () => ({
  billingService: {
    listInvoices: vi.fn(),
    downloadInvoicePdf: vi.fn(),
  },
}));

vi.mock('../employee/employeeService.js', () => ({
  employeeService: {
    limit: vi.fn(),
  },
}));

vi.mock('../admin/tenantService.js', () => ({
  tenantService: {
    subscription: vi.fn(),
  },
}));

const mockInvoices = [
  {
    id: 'inv-1',
    tenantId: 't-1',
    invoiceNumber: 'INF/2026-27/0001',
    invoiceDate: '2026-04-01',
    dueDate: '2026-04-15',
    billingPeriodStart: '2026-04-01',
    billingPeriodEnd: '2026-04-30',
    planTier: 'GROWTH',
    billingFrequency: 'MONTHLY',
    activeHeadcount: 25,
    subtotal: 1975,
    discountAmount: 0,
    taxableAmount: 1975,
    cgstAmount: 177.75,
    sgstAmount: 177.75,
    igstAmount: 0,
    totalTax: 355.5,
    totalAmount: 2330.5,
    amountPaid: 0,
    currency: 'INR',
    status: 'ISSUED',
    customerGstin: '29ABCDE1234F1Z5',
    sacCode: '997331',
    items: [
      {
        description: 'Growth Plan (Monthly) - 25 users',
        quantity: 25,
        unitPrice: 79,
        amount: 1975,
      },
    ],
  },
  {
    id: 'inv-2',
    tenantId: 't-1',
    invoiceNumber: 'INF/2026-27/0002',
    invoiceDate: '2026-03-01',
    dueDate: '2026-03-15',
    billingPeriodStart: '2026-03-01',
    billingPeriodEnd: '2026-03-31',
    planTier: 'GROWTH',
    billingFrequency: 'MONTHLY',
    activeHeadcount: 20,
    subtotal: 1975,
    discountAmount: 0,
    taxableAmount: 1975,
    cgstAmount: 177.75,
    sgstAmount: 177.75,
    igstAmount: 0,
    totalTax: 355.5,
    totalAmount: 2330.5,
    amountPaid: 2330.5,
    currency: 'INR',
    status: 'PAID',
  },
];

function setup({ canRead = true, invoices = mockInvoices, limit = { limit: 50, used: 25 }, sub = null } = {}) {
  vi.spyOn(screens, 'useCan').mockImplementation((code) => {
    if (code === 'core.tenant.read' || code === 'core.tenant.view') return canRead;
    return false;
  });

  billingService.listInvoices.mockResolvedValue(invoices);
  billingService.downloadInvoicePdf.mockResolvedValue(new Blob(['pdf']));
  employeeService.limit.mockResolvedValue(limit);
  tenantService.subscription.mockResolvedValue(
    sub || {
      plan: 'GROWTH',
      supportPlan: 'PRIORITY',
      employeeLimit: 50,
      status: 'ACTIVE',
    }
  );

  const store = configureStore({
    reducer: {
      impersonation: () => ({ session: { tenantId: 't-1' } }),
    },
  });

  return render(
    <Provider store={store}>
      <TenantBillingPage />
    </Provider>
  );
}

describe('TenantBillingPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders NotEntitled when user lacks read permission', () => {
    setup({ canRead: false });
    expect(screen.getByText('Module Not Subscribed')).toBeDefined();
  });

  it('renders Subscription & Billing title and Current Subscription Plan card', async () => {
    setup();

    expect(await screen.findByText('Subscription & Billing')).toBeDefined();
    expect(screen.getByText('Current Subscription Plan')).toBeDefined();
    expect(screen.getByText('Growth')).toBeDefined();
    expect(screen.getByText('Priority')).toBeDefined();
    // Headcount text: 25 / 50
    expect(screen.getByText('25 / 50')).toBeDefined();
  });

  it('renders invoices table with formatted INR currency and status tags', async () => {
    setup();

    expect(await screen.findByText('INF/2026-27/0001')).toBeDefined();
    expect(screen.getByText('INF/2026-27/0002')).toBeDefined();

    // Check INR formatted currency
    const totalAmounts = screen.getAllByText(/₹\s*2,330\.50/);
    expect(totalAmounts.length).toBeGreaterThanOrEqual(1);

    // Status tags
    expect(screen.getByText('Issued')).toBeDefined();
    expect(screen.getByText('Paid')).toBeDefined();
  });

  it('triggers invoice PDF download when clicking Download PDF', async () => {
    setup();

    const downloadButtons = await screen.findAllByRole('button', { name: /Download PDF/i });
    expect(downloadButtons.length).toBeGreaterThan(0);

    fireEvent.click(downloadButtons[0]);

    await waitFor(() => {
      expect(billingService.downloadInvoicePdf).toHaveBeenCalledWith(mockInvoices[0]);
    });
  });

  it('renders Empty state when there are no invoices', async () => {
    setup({ invoices: [] });

    expect(await screen.findByText('No invoices issued yet.')).toBeDefined();
  });
});
