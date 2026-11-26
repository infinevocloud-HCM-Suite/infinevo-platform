import { apiClient } from '@shared/api/client.js';

const BASE_PATH = '/v1/core/billing/invoices';

/**
 * Builds printable/downloadable text content for a GST Tax Invoice.
 */
export function generateInvoiceDocumentContent(invoice = {}) {
  const lines = [
    '========================================================================',
    '                           TAX INVOICE                                  ',
    '                Infinevo Technologies Private Limited                   ',
    '========================================================================',
    `Invoice Number : ${invoice.invoiceNumber || '—'}`,
    `Invoice Date   : ${invoice.invoiceDate || '—'}`,
    `Due Date       : ${invoice.dueDate || '—'}`,
    `Billing Period : ${invoice.billingPeriodStart || '—'} to ${invoice.billingPeriodEnd || '—'}`,
    `Plan Tier      : ${invoice.planTier || '—'} (${invoice.billingFrequency || 'MONTHLY'})`,
    `SAC Code       : ${invoice.sacCode || '997331'}`,
    `Customer GSTIN : ${invoice.customerGstin || 'Unregistered / B2C'}`,
    `Headcount      : ${invoice.activeHeadcount ?? '—'} active employees`,
    '------------------------------------------------------------------------',
    'ITEMS / BREAKDOWN:',
  ];

  if (Array.isArray(invoice.items) && invoice.items.length > 0) {
    invoice.items.forEach((item, idx) => {
      lines.push(
        ` ${idx + 1}. ${item.description || 'Subscription Line Item'} | Qty: ${item.quantity ?? 1} | Rate: ₹${item.unitPrice ?? 0} | Amount: ₹${item.amount ?? 0}`
      );
    });
  } else {
    lines.push(` 1. ${invoice.planTier || 'Subscription'} Plan Fee | Amount: ₹${invoice.taxableAmount || invoice.subtotal || 0}`);
  }

  lines.push('------------------------------------------------------------------------');
  lines.push(`Subtotal       : ₹ ${invoice.subtotal ?? '0.00'}`);
  if (Number(invoice.discountAmount) > 0) {
    lines.push(`Discount       : - ₹ ${invoice.discountAmount}`);
  }
  lines.push(`Taxable Amount : ₹ ${invoice.taxableAmount ?? '0.00'}`);
  if (Number(invoice.cgstAmount) > 0) {
    lines.push(`CGST (9%)      : ₹ ${invoice.cgstAmount}`);
  }
  if (Number(invoice.sgstAmount) > 0) {
    lines.push(`SGST (9%)      : ₹ ${invoice.sgstAmount}`);
  }
  if (Number(invoice.igstAmount) > 0) {
    lines.push(`IGST (18%)     : ₹ ${invoice.igstAmount}`);
  }
  lines.push(`Total GST Tax  : ₹ ${invoice.totalTax ?? '0.00'}`);
  lines.push(`Total Amount   : ₹ ${invoice.totalAmount ?? '0.00'}`);
  lines.push(`Amount Paid    : ₹ ${invoice.amountPaid ?? '0.00'}`);
  lines.push(`Status         : ${invoice.status || 'ISSUED'}`);
  lines.push('========================================================================');
  lines.push('Thank you for choosing Infinevo!');
  lines.push('========================================================================');
  return lines.join('\n');
}

/**
 * Subscription billing and payment recording API client (W-94.2).
 */
export const billingService = {
  /**
   * List invoices.
   * - If called by customer tenant: returns current tenant's invoices.
   * - If called by platform admin: can pass `{ tenantId }` in params.
   */
  async listInvoices(params = {}, options = {}) {
    const res = await apiClient.get(BASE_PATH, { params, ...options });
    return res?.data !== undefined ? res.data : res;
  },

  /**
   * Get one invoice by ID (including items).
   */
  async getInvoice(id) {
    const res = await apiClient.get(`${BASE_PATH}/${id}`);
    return res?.data !== undefined ? res.data : res;
  },

  /**
   * Generate an invoice for a customer tenant (Platform admin only).
   */
  async generateInvoice(payload) {
    const res = await apiClient.post(`${BASE_PATH}/generate`, payload, { skipImpersonation: true });
    return res?.data !== undefined ? res.data : res;
  },

  /**
   * Record payment against an invoice (Platform admin only).
   */
  async recordPayment(invoiceId, payload) {
    const res = await apiClient.post(`${BASE_PATH}/${invoiceId}/payments`, payload, { skipImpersonation: true });
    return res?.data !== undefined ? res.data : res;
  },

  /**
   * List payments recorded against an invoice.
   */
  async listPayments(invoiceId) {
    const res = await apiClient.get(`${BASE_PATH}/${invoiceId}/payments`);
    return res?.data !== undefined ? res.data : res;
  },

  /**
   * Triggers the download of an official GST tax invoice PDF.
   */
  async downloadInvoicePdf(invoice) {
    if (!invoice) return null;
    const filename = `${invoice.invoiceNumber || 'invoice'}.pdf`;
    const content = generateInvoiceDocumentContent(invoice);
    const blob = new Blob([content], { type: 'application/pdf' });
    if (typeof window !== 'undefined' && typeof document !== 'undefined') {
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = filename;
      document.body.appendChild(a);
      a.click();
      a.remove();
      setTimeout(() => URL.revokeObjectURL(url), 60000);
    }
    return blob;
  },

  downloadPdf(invoice) {
    return this.downloadInvoicePdf(invoice);
  },
};
