import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { MobileShell } from './MobileShell.jsx';

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

vi.mock('../shell/portal/PortalLayout.jsx', () => ({
  PortalLayout: () => <div data-testid="portal-layout">Portal Content</div>,
}));

vi.mock('./MobileTabBar.jsx', () => ({
  MobileTabBar: () => <nav data-testid="mock-tabbar">Mobile Tab Bar</nav>,
}));

describe('MobileShell', () => {
  beforeEach(() => {
    document.body.className = '';
  });

  it('renders mobile container, header with Infinevo brand, and tab bar', () => {
    render(
      <MemoryRouter initialEntries={['/me']}>
        <MobileShell />
      </MemoryRouter>,
    );

    expect(screen.getByTestId('mobile-shell')).toBeTruthy();
    expect(screen.getByText('Infinevo')).toBeTruthy();
    expect(screen.getByText('Krushna Sonalkar')).toBeTruthy();
    expect(screen.getByTestId('mock-tabbar')).toBeTruthy();
  });

  it('adds mobile-native class to body and removes it on unmount', () => {
    const { unmount } = render(
      <MemoryRouter initialEntries={['/me']}>
        <MobileShell />
      </MemoryRouter>,
    );

    expect(document.body.classList.contains('mobile-native')).toBe(true);

    unmount();
    expect(document.body.classList.contains('mobile-native')).toBe(false);
  });

  it('renders children passed into MobileShell', () => {
    render(
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
    render(
      <MemoryRouter initialEntries={['/me/attendance']}>
        <MobileShell />
      </MemoryRouter>,
    );

    expect(
      screen.getByTestId('mobile-attendance-loading') || screen.getByTestId('mobile-attendance-screen'),
    ).toBeTruthy();
  });
});
