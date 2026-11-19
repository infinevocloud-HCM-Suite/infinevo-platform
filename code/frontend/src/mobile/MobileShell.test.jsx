import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { MobileShell } from './MobileShell.jsx';
import * as mobileAuthModule from './mobileAuth.js';
import * as apiClientModule from '../shared/api/client.js';
import approvalReducer, { decrementPendingCount } from '../core/approvals/approvalSlice.js';
import { mobileApprovalService } from './approvals/mobileApprovalService.js';
import { mobilePayslipService } from './payslips/mobilePayslipService.js';

vi.mock('../shell/auth/useMe.js', () => ({
  useMe: () => ({
    displayName: 'Krushna Sonalkar',
    email: 'ksonalkar@infinevo.com',
    roles: ['employee'],
  }),
}));

vi.mock('../shell/auth/keycloak.js', () => ({
  logout: vi.fn(),
  keycloak: {},
  onTenantChange: vi.fn(() => () => {}),
}));

vi.mock('./mobileAuth.js', () => ({
  isMobileAuthenticated: vi.fn(() => true),
  startMobileLogin: vi.fn(() => Promise.resolve()),
  clearStoredTokens: vi.fn(() => Promise.resolve()),
  mobileLogout: vi.fn(() => Promise.resolve()),
}));

vi.mock('../shell/navigation/useCan.js', () => ({
  useCan: vi.fn(() => true),
}));

vi.mock('./approvals/mobileApprovalService.js', () => ({
  mobileApprovalService: {
    getPendingApprovals: vi.fn(() => Promise.resolve({ totalElements: 5, rows: [] })),
  },
}));

vi.mock('./payslips/mobilePayslipService.js', () => ({
  mobilePayslipService: {
    cleanup: vi.fn(() => Promise.resolve()),
  },
}));

vi.mock('../shell/portal/PortalLayout.jsx', () => ({
  PortalLayout: () => <div data-testid="portal-layout">Portal Content</div>,
}));

vi.mock('./MobileTabBar.jsx', () => ({
  // eslint-disable-next-line react/prop-types
  MobileTabBar: ({ pendingBadgeCount = 0 }) => (
    <nav data-testid="mock-tabbar">
      <span>Mobile Tab Bar</span>
      <span data-testid="mock-tabbar-badge">{pendingBadgeCount}</span>
    </nav>
  ),
}));

function renderMobileShell(ui, { preloadedState = {} } = {}) {
  const store = configureStore({
    reducer: {
      approvals: approvalReducer,
    },
    preloadedState,
  });
  return {
    store,
    ...render(<Provider store={store}>{ui}</Provider>),
  };
}

describe('MobileShell (W-98.5 §7)', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    document.body.className = '';
    vi.spyOn(mobileAuthModule, 'isMobileAuthenticated').mockReturnValue(true);
  });

  it('renders mobile container, header with Infinevo brand, and tab bar', async () => {
    renderMobileShell(
      <MemoryRouter initialEntries={['/me']}>
        <MobileShell />
      </MemoryRouter>,
    );

    expect(screen.getByTestId('mobile-shell')).toBeTruthy();
    expect(screen.getByText('Infinevo')).toBeTruthy();
    expect(screen.getByText('Krushna Sonalkar')).toBeTruthy();
    expect(screen.getByTestId('mock-tabbar')).toBeTruthy();
  });

  it('tab badge shows pending count and drops by one after a decision (W-98.5 §7)', async () => {
    mobileApprovalService.getPendingApprovals.mockResolvedValueOnce({
      totalElements: 4,
      rows: [],
    });

    const { store } = renderMobileShell(
      <MemoryRouter initialEntries={['/me']}>
        <MobileShell />
      </MemoryRouter>,
      { preloadedState: { approvals: { pendingCount: 4 } } },
    );

    // Initial badge matches pending count
    await waitFor(() => {
      expect(screen.getByTestId('mock-tabbar-badge').textContent).toBe('4');
    });

    // Simulating decision: decrementPendingCount dispatched
    act(() => {
      store.dispatch(decrementPendingCount());
    });

    await waitFor(() => {
      expect(screen.getByTestId('mock-tabbar-badge').textContent).toBe('3');
    });
  });

  it('calls mobilePayslipService.cleanup on mount (W-98.4 §2)', () => {
    renderMobileShell(
      <MemoryRouter initialEntries={['/me']}>
        <MobileShell />
      </MemoryRouter>,
    );

    expect(mobilePayslipService.cleanup).toHaveBeenCalled();
  });

  it('adds mobile-native class to body and removes it on unmount', () => {
    const { unmount } = renderMobileShell(
      <MemoryRouter initialEntries={['/me']}>
        <MobileShell />
      </MemoryRouter>,
    );

    expect(document.body.classList.contains('mobile-native')).toBe(true);

    unmount();
    expect(document.body.classList.contains('mobile-native')).toBe(false);
  });

  it('renders children passed into MobileShell', () => {
    renderMobileShell(
      <MemoryRouter initialEntries={['/me']}>
        <MobileShell>
          <div data-testid="custom-child">Custom Mobile View</div>
        </MobileShell>
      </MemoryRouter>,
    );

    expect(screen.getByTestId('custom-child')).toBeTruthy();
    expect(screen.getByText('Custom Mobile View')).toBeTruthy();
  });

  it('mounts MobileAttendanceScreen on /me/attendance (W-98.2)', () => {
    renderMobileShell(
      <MemoryRouter initialEntries={['/me/attendance']}>
        <MobileShell />
      </MemoryRouter>,
    );

    expect(
      screen.getByTestId('mobile-attendance-loading') || screen.getByTestId('mobile-attendance-screen'),
    ).toBeTruthy();
  });

  it('mounts MobileLeaveScreen on /me/leave (W-98.3)', () => {
    renderMobileShell(
      <MemoryRouter initialEntries={['/me/leave']}>
        <MobileShell />
      </MemoryRouter>,
    );

    expect(
      screen.getByTestId('mobile-leave-loading') || screen.getByTestId('mobile-leave-screen'),
    ).toBeTruthy();
  });

  it('renders sign in retry screen when not authenticated or cancelled', async () => {
    vi.spyOn(mobileAuthModule, 'isMobileAuthenticated').mockReturnValue(false);

    renderMobileShell(
      <MemoryRouter initialEntries={['/me']}>
        <MobileShell initialAuthenticated={false} />
      </MemoryRouter>,
    );

    expect(await screen.findByTestId('mobile-signin-screen')).toBeTruthy();
    expect(screen.getByText('Sign In to Infinevo')).toBeTruthy();

    const signInBtn = screen.getByRole('button', { name: /sign in/i });
    fireEvent.click(signInBtn);

    await waitFor(() => {
      expect(mobileAuthModule.startMobileLogin).toHaveBeenCalled();
    });
  });

  it('registers 401 unauthorized handler that clears tokens and triggers login', async () => {
    let capturedHandler = null;
    vi.spyOn(apiClientModule, 'setUnauthorizedHandler').mockImplementation((handler) => {
      capturedHandler = handler;
    });

    renderMobileShell(
      <MemoryRouter initialEntries={['/me']}>
        <MobileShell />
      </MemoryRouter>,
    );

    expect(capturedHandler).toBeDefined();

    // Trigger the 401 callback
    await capturedHandler();

    expect(mobileAuthModule.clearStoredTokens).toHaveBeenCalled();
    expect(mobileAuthModule.startMobileLogin).toHaveBeenCalled();
  });
});
