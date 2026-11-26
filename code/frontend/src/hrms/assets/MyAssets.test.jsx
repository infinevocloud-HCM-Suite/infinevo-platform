import { render, screen, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { MyAssets } from './MyAssets.jsx';
import { assetService } from './assetService.js';

vi.mock('./assetService.js', () => ({
  assetService: {
    getMyAssets: vi.fn(),
  },
}));

describe('MyAssets component (W-111)', () => {
  const mockAssignedAssets = [
    {
      id: 'ast-10',
      assetTag: 'LAP-2026-042',
      assetName: 'ThinkPad T14s Gen 4',
      brand: 'Lenovo',
      modelNumber: '21F8',
      serialNumber: 'PF-498211',
      category: 'LAPTOP',
      currentStatus: 'ASSIGNED',
      itemCondition: 'GOOD',
      activeAssignment: {
        id: 'asg-1',
        assignedDate: '2026-04-10',
        assignmentCondition: 'GOOD',
        assignmentNotes: 'Includes 65W USB-C charger and laptop sleeve',
      },
    },
  ];

  beforeEach(() => {
    vi.clearAllMocks();
    assetService.getMyAssets.mockResolvedValue(mockAssignedAssets);
  });

  it('renders assigned equipment card and details for signed-in employee', async () => {
    render(
      <MemoryRouter>
        <MyAssets />
      </MemoryRouter>
    );

    await waitFor(() => {
      expect(screen.getAllByText('LAP-2026-042').length).toBeGreaterThan(0);
      expect(screen.getAllByText('ThinkPad T14s Gen 4').length).toBeGreaterThan(0);
      expect(screen.getAllByText('PF-498211').length).toBeGreaterThan(0);
      expect(screen.getByText('Includes 65W USB-C charger and laptop sleeve')).toBeTruthy();
    });

    expect(screen.getByText('My Assigned Equipment')).toBeTruthy();
  });
});

