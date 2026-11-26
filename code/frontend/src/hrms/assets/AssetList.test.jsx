import { render, screen, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { AssetList } from './AssetList.jsx';
import { assetService } from './assetService.js';

vi.mock('./assetService.js', () => ({
  assetService: {
    list: vi.fn(),
    create: vi.fn(),
    assign: vi.fn(),
    returnAsset: vi.fn(),
  },
}));

vi.mock('@shell/screens', () => ({
  useCan: vi.fn(() => true),
}));

describe('AssetList component (W-111)', () => {
  const mockAssets = [
    {
      id: 'ast-1',
      assetTag: 'LAP-001',
      assetName: 'Apple MacBook Pro 14',
      brand: 'Apple',
      modelNumber: 'A2442',
      serialNumber: 'C02G1234MD6R',
      category: 'LAPTOP',
      currentStatus: 'AVAILABLE',
      itemCondition: 'NEW',
      activeAssignment: null,
    },
    {
      id: 'ast-2',
      assetTag: 'MON-002',
      assetName: 'Dell UltraSharp 27',
      brand: 'Dell',
      modelNumber: 'U2723QE',
      serialNumber: 'CN-099411',
      category: 'MONITOR',
      currentStatus: 'ASSIGNED',
      itemCondition: 'GOOD',
      activeAssignment: {
        id: 'assign-1',
        employeeId: 'emp-101',
        employeeName: 'Sarah Connor',
        assignedDate: '2026-04-01',
        assignmentCondition: 'GOOD',
      },
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
    assetService.list.mockResolvedValue(mockAssets);
  });

  it('renders asset inventory list with tags, names, status pills, and custodians', async () => {
    render(
      <MemoryRouter>
        <AssetList />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getByText('LAP-001')).toBeTruthy();
      expect(screen.getByText('Apple MacBook Pro 14')).toBeTruthy();
      expect(screen.getByText('MON-002')).toBeTruthy();
      expect(screen.getByText('Dell UltraSharp 27')).toBeTruthy();
      expect(screen.getByText('Available')).toBeTruthy();
      expect(screen.getByText('Assigned')).toBeTruthy();
      expect(screen.getByText('Sarah Connor')).toBeTruthy();
    });

    expect(screen.getByText('Register Asset')).toBeTruthy();
    expect(screen.getByText('Assign')).toBeTruthy();
    expect(screen.getByText('Return')).toBeTruthy();
  });
});

