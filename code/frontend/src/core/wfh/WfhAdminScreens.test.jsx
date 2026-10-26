import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { WfhPolicies } from './WfhPolicies.jsx';
import { WfhAllocations } from './WfhAllocations.jsx';
import { WfhRequests } from './WfhRequests.jsx';
import { WfhImport } from './WfhImport.jsx';
import { wfhService } from './wfhService.js';
import * as useCanModule from '@shell/screens';

vi.mock('./wfhService.js', () => ({
  wfhService: {
    getPolicies: vi.fn(),
    createPolicy: vi.fn(),
    getAllocations: vi.fn(),
    allocateQuota: vi.fn(),
    getAllRequests: vi.fn(),
    getTeamRequests: vi.fn(),
    applyOnBehalf: vi.fn(),
    getImportLogs: vi.fn(),
    importCsv: vi.fn(),
    getSampleTemplate: vi.fn(),
  },
}));

describe('WFH Admin Screens Layout & Leave Parity (D-107)', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(useCanModule, 'useCan').mockReturnValue(true);
  });

  describe('WfhPolicies Screen', () => {
    it('renders full-width card, header with Add button, and policies table', async () => {
      wfhService.getPolicies.mockResolvedValue([
        {
          id: 'pol-1',
          code: 'STD_WFH',
          name: 'Standard Remote Work',
          annualDays: 12,
          monthlyLimitDays: 2,
          allowHalfDay: true,
          active: true,
        },
      ]);

      render(<WfhPolicies />);

      await waitFor(() => {
        expect(screen.getByText('WFH Policies')).toBeDefined();
        expect(screen.getByText('Standard Remote Work')).toBeDefined();
      });

      expect(screen.getByTestId('create-wfh-policy-btn')).toBeDefined();
      expect(screen.getByTestId('wfh-policies-table')).toBeDefined();
      expect(screen.getByText('12 days/yr')).toBeDefined();
    });
  });

  describe('WfhAllocations Screen', () => {
    it('renders full-width card, toolbar with year filter & Allocate button, and allocations table', async () => {
      wfhService.getAllocations.mockResolvedValue([
        {
          id: 'alloc-1',
          employeeName: 'Krushna Sonalkar',
          employeeNumber: 'EMP001',
          wfhYear: '2026',
          yearStartDate: '2026-01-01',
          yearEndDate: '2026-12-31',
          entitlementDays: 12,
          proRateFactor: 1.0,
          createdAt: '2026-01-01T00:00:00Z',
        },
      ]);

      render(<WfhAllocations />);

      await waitFor(() => {
        expect(screen.getByText('WFH Allocations')).toBeDefined();
        expect(screen.getByText('Krushna Sonalkar')).toBeDefined();
      });

      expect(screen.getByTestId('wfh-year-filter')).toBeDefined();
      expect(screen.getByTestId('create-wfh-allocation-btn')).toBeDefined();
      expect(screen.getByTestId('wfh-allocations-table')).toBeDefined();
      expect(screen.getByText('12 days')).toBeDefined();
    });
  });

  describe('WfhRequests Screen', () => {
    it('renders full-width card, header with Apply on Behalf, horizontal tabs, and requests table', async () => {
      wfhService.getAllRequests.mockResolvedValue([
        {
          id: 'req-1',
          employeeName: 'Krushna Sonalkar',
          employeeNumber: 'EMP001',
          fromDate: '2026-11-05',
          toDate: '2026-11-06',
          workingDays: 2,
          reason: 'Deep work sprint',
          status: 'PENDING',
          onBehalf: false,
          createdAt: '2026-10-23T10:00:00Z',
        },
      ]);

      render(<WfhRequests />);

      await waitFor(() => {
        expect(screen.getByText('WFH Applications')).toBeDefined();
        expect(screen.getByText('Deep work sprint')).toBeDefined();
      });

      expect(screen.getByTestId('apply-on-behalf-btn')).toBeDefined();
      expect(screen.getByTestId('all-wfh-requests-table')).toBeDefined();
      expect(screen.getByText('All Requests')).toBeDefined();
      expect(screen.getAllByText('Pending Approval').length).toBeGreaterThanOrEqual(1);
    });
  });

  describe('WfhImport Screen', () => {
    it('renders dual-card layout: Import Configuration and Past Import History', async () => {
      wfhService.getImportLogs.mockResolvedValue([
        {
          id: 'log-1',
          fileName: 'wfh_2026.csv',
          wfhYear: '2026',
          isDryRun: true,
          rowsTotal: 50,
          rowsImported: 50,
          rowsFailed: 0,
          status: 'COMPLETED',
          startedAt: '2026-10-23T10:00:00Z',
        },
      ]);

      render(<WfhImport />);

      await waitFor(() => {
        expect(screen.getByText('Bulk WFH Quota Import')).toBeDefined();
        expect(screen.getByText('Import Configuration')).toBeDefined();
        expect(screen.getByText('Past Import History')).toBeDefined();
      });

      expect(screen.getByTestId('download-wfh-template-btn')).toBeDefined();
      expect(screen.getByTestId('upload-wfh-csv-btn')).toBeDefined();
      expect(screen.getByTestId('wfh-import-logs-table')).toBeDefined();
      expect(screen.getByText('Dry Run (Validate without saving)')).toBeDefined();
      expect(screen.getByText('wfh_2026.csv')).toBeDefined();
    });
  });
});
