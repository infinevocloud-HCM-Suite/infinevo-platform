import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, act, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import fs from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { Provider } from 'react-redux';
import { store } from './store.js';
import { AppShell } from './AppShell.jsx';
import * as navigationModule from './navigation/useNavigation.js';
import * as meModule from './auth/useMe.js';
import * as clientModule from '../shared/api/client.js';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

describe('AppShell component', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(clientModule.apiClient, 'get').mockResolvedValue({ data: { content: [], totalElements: 0 } });
  });

  it('renders NoModules screen when navigation feed is empty and loading is complete', () => {
    vi.spyOn(navigationModule, 'useNavigation').mockReturnValue({
      items: [],
      loading: false,
    });

    render(
      <MemoryRouter initialEntries={['/']}>
        <AppShell />
      </MemoryRouter>,
    );

    expect(screen.getByText('No Modules Available')).toBeDefined();
  });

  it('renders NotFound screen on unknown routes when feed has items', () => {
    vi.spyOn(navigationModule, 'useNavigation').mockReturnValue({
      items: [{ key: 'core', labelKey: 'Core', path: '/core' }],
      loading: false,
    });

    render(
      <MemoryRouter initialEntries={['/unknown-path-123']}>
        <AppShell />
      </MemoryRouter>,
    );

    expect(screen.getByText('404')).toBeDefined();
  });

  it('opens the first feed path at the root instead of NotFound', async () => {
    vi.spyOn(navigationModule, 'useNavigation').mockReturnValue({
      items: [
        { key: 'org', labelKey: 'nav.organisation', children: [{ key: 'emp', labelKey: 'Employees', path: '/employees' }] },
      ],
      loading: false,
    });

    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/']}>
          <AppShell />
        </MemoryRouter>
      </Provider>,
    );

    expect(screen.queryByText('404')).toBeNull();
    expect(await screen.findByRole('heading', { name: /Employees/i }, { timeout: 5000 })).toBeDefined();
  });

  it('opens the home path the feed names at the root, not the first menu entry (D-35)', async () => {
    vi.spyOn(navigationModule, 'useNavigation').mockReturnValue({
      items: [
        { key: 'core.org', labelKey: 'nav.organisation', children: [{ key: 'core.roles', labelKey: 'nav.roles', path: '/roles' }] },
        { key: 'core.people', labelKey: 'nav.people', children: [{ key: 'core.employee', labelKey: 'nav.employees', path: '/employees' }] },
      ],
      homePath: '/employees',
      loading: false,
    });

    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/']}>
          <AppShell />
        </MemoryRouter>
      </Provider>,
    );

    expect(await screen.findByRole('heading', { name: /Employees/i }, { timeout: 5000 })).toBeDefined();
  });

  it('sends an employee with an empty menu from the root to the portal home the feed names (D-35)', () => {
    vi.spyOn(navigationModule, 'useNavigation').mockReturnValue({
      items: [],
      homePath: '/me',
      loading: false,
    });

    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/']}>
          <AppShell />
        </MemoryRouter>
      </Provider>,
    );

    expect(screen.queryByText('No Modules Available')).toBeNull();
  });

  it('keeps menu groups collapsed except the one holding the current screen (D-34)', () => {
    vi.spyOn(navigationModule, 'useNavigation').mockReturnValue({
      items: [
        {
          key: 'core.people',
          labelKey: 'nav.people',
          path: '/core-people',
          children: [{ key: 'core.employee', labelKey: 'nav.employees', path: '/core-people' }],
        },
        {
          key: 'core.settings',
          labelKey: 'nav.settings',
          path: '/core-audit',
          children: [{ key: 'core.audit', labelKey: 'nav.audit', path: '/core-audit' }],
        },
      ],
      loading: false,
    });

    const { container } = render(
      <MemoryRouter initialEntries={['/core-audit']}>
        <AppShell />
      </MemoryRouter>,
    );

    const labels = Array.from(container.querySelectorAll('.ant-menu-item')).map((el) => el.textContent.trim());
    expect(labels).toContain('Audit log');
    expect(labels).not.toContain('Employees');
    const titles = Array.from(container.querySelectorAll('.ant-menu-submenu-title')).map((el) => el.textContent.trim());
    expect(titles).toEqual(['People', 'Settings']);
  });

  it('selects the menu item with the longest matching path, not the first prefix (W-73.2)', () => {
    vi.spyOn(navigationModule, 'useNavigation').mockReturnValue({
      items: [
        { key: 'core.admin.home', labelKey: 'nav.admin.home', path: '/zz-admin' },
        { key: 'core.tenants', labelKey: 'nav.tenants', path: '/zz-admin/tenants' },
        { key: 'core.other', labelKey: 'nav.roles', path: '/zz-admin-other' },
      ],
      loading: false,
    });

    const { container, unmount } = render(
      <MemoryRouter initialEntries={['/zz-admin/tenants/abc']}>
        <AppShell />
      </MemoryRouter>,
    );
    const selected = () =>
      Array.from(container.querySelectorAll('.ant-menu-item-selected')).map((el) => el.textContent.trim());
    expect(selected()).toEqual(['Tenants']);
    unmount();

    const second = render(
      <MemoryRouter initialEntries={['/zz-admin']}>
        <AppShell />
      </MemoryRouter>,
    );
    expect(
      Array.from(second.container.querySelectorAll('.ant-menu-item-selected')).map((el) => el.textContent.trim()),
    ).toEqual(['Dashboard']);
  });

  it('shows the tenant name from the feed in the header', () => {
    vi.spyOn(navigationModule, 'useNavigation').mockReturnValue({
      items: [{ key: 'core', labelKey: 'Core', path: '/core' }],
      tenantName: 'Acme Ltd',
      loading: false,
    });

    render(
      <MemoryRouter initialEntries={['/core']}>
        <AppShell />
      </MemoryRouter>,
    );

    expect(screen.getByTestId('tenant-display').textContent).toContain('Acme Ltd');
  });

  it('D-75: the portal item stays selected on /me screens, and My profile opens /me/profile', async () => {
    vi.spyOn(navigationModule, 'useNavigation').mockReturnValue({
      items: [
        { key: 'core.me', labelKey: 'nav.me', path: '/me' },
        { key: 'core.org.departments', labelKey: 'nav.departments', path: '/zz-departments' },
      ],
      actions: ['core.employee.read_own'],
      homePath: '/me',
      loading: false,
    });
    const selected = (container) =>
      Array.from(container.querySelectorAll('.ant-menu-item-selected')).map((el) => el.textContent.trim());

    const first = render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/me/leave']}>
          <AppShell />
        </MemoryRouter>
      </Provider>,
    );
    expect(selected(first.container)).toEqual(['My self-service']);
    first.unmount();

    const { container } = render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/zz-departments']}>
          <AppShell />
        </MemoryRouter>
      </Provider>,
    );
    expect(selected(container)).toEqual(['Departments']);
    fireEvent.click(screen.getByRole('button', { name: 'User menu' }));
    fireEvent.click(await screen.findByText('My profile'));
    expect(selected(container)).toEqual(['My self-service']);
  });

  it('D-75: no My profile in the user menu when the feed has no portal item, even with core.employee.read_own', async () => {
    vi.spyOn(navigationModule, 'useNavigation').mockReturnValue({
      items: [{ key: 'core.org.departments', labelKey: 'nav.departments', path: '/zz-departments' }],
      actions: ['core.employee.read_own', 'core.employee.read', 'core.org.read'],
      loading: false,
    });

    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/zz-departments']}>
          <AppShell />
        </MemoryRouter>
      </Provider>,
    );
    fireEvent.click(screen.getByRole('button', { name: 'User menu' }));

    expect(await screen.findByText('Sign out')).toBeDefined();
    expect(screen.queryByText('My profile')).toBeNull();
  });

  it('mounts a route from module index when the feed names its path', async () => {
    vi.spyOn(navigationModule, 'useNavigation').mockReturnValue({
      items: [{ key: 'employees', labelKey: 'Employees', path: '/employees' }],
      loading: false,
    });

    render(
      <Provider store={store}>
        <MemoryRouter initialEntries={['/employees']}>
          <AppShell />
        </MemoryRouter>
      </Provider>,
    );

    // The route is lazy-loaded: allow the import more than findBy's default second.
    expect(await screen.findByRole('heading', { name: /Employees/i }, { timeout: 5000 })).toBeDefined();
  });

  it('renders Suspended screen full-page without sidebar or clickable menu when suspended', () => {
    let capturedSuspendedHandler;
    vi.spyOn(clientModule, 'setTenantSuspendedHandler').mockImplementation((fn) => {
      capturedSuspendedHandler = fn;
    });

    vi.spyOn(navigationModule, 'useNavigation').mockReturnValue({
      items: [{ key: 'core', labelKey: 'Core Menu Item', path: '/core' }],
      loading: false,
    });

    render(
      <MemoryRouter initialEntries={['/core']}>
        <AppShell />
      </MemoryRouter>,
    );

    expect(screen.getByText('Core Menu Item')).toBeDefined();

    act(() => {
      if (capturedSuspendedHandler) capturedSuspendedHandler();
    });

    expect(screen.getByText('Subscription Suspended')).toBeDefined();
    expect(screen.queryByText('Core Menu Item')).toBeNull();
  });

  it('contains no hex color literals in shell JSX source files (W-45 §8)', () => {
    const shellFiles = [
      'AppShell.jsx',
      'Header.jsx',
      'ShellBoundary.jsx',
      'screens/Suspended.jsx',
      'screens/NotEntitled.jsx',
      'screens/NotFound.jsx',
      'screens/NoModules.jsx',
    ];
    const hexRegex = /#[0-9a-fA-F]{3,6}\b/;

    shellFiles.forEach((filename) => {
      const source = fs.readFileSync(resolve(__dirname, filename), 'utf-8');
      expect(source).not.toMatch(hexRegex);
    });
  });

  describe('welcome page (W-73.8)', () => {
    const adminFeed = {
      items: [{ key: 'core.setup', labelKey: 'nav.setup', path: '/setup' }],
      homePath: '/setup',
      tenantName: 'Acme Ltd',
      loading: false,
    };

    function meSays(overrides) {
      return vi.spyOn(meModule, 'useMe').mockReturnValue({
        displayName: 'Ana Acme',
        roles: ['tenant-admin'],
        email: null,
        welcomeSeen: false,
        loading: false,
        error: null,
        refetch: vi.fn(),
        ...overrides,
      });
    }

    function renderAt(path) {
      return render(
        <Provider store={store}>
          <MemoryRouter initialEntries={[path]}>
            <AppShell />
          </MemoryRouter>
        </Provider>,
      );
    }

    it('sends / to /welcome while /me says the page has not been seen', async () => {
      vi.spyOn(navigationModule, 'useNavigation').mockReturnValue(adminFeed);
      meSays({ welcomeSeen: false });
      renderAt('/');
      expect(await screen.findByTestId('welcome-screen')).toBeDefined();
      expect(screen.getByTestId('welcome-card-admin')).toBeDefined();
    });

    it('sends / to the home path once it has been seen', async () => {
      vi.spyOn(navigationModule, 'useNavigation').mockReturnValue({
        items: [{ key: 'core.employee', labelKey: 'nav.employees', path: '/employees' }],
        homePath: '/employees',
        loading: false,
      });
      meSays({ roles: ['hr'], welcomeSeen: true });
      renderAt('/');
      expect(await screen.findByRole('heading', { name: /Employees/i }, { timeout: 5000 })).toBeDefined();
      expect(screen.queryByTestId('welcome-screen')).toBeNull();
    });

    it('waits for /me before deciding where / goes', async () => {
      vi.spyOn(navigationModule, 'useNavigation').mockReturnValue(adminFeed);
      // Not seen, but still loading: without the wait this would open the (eager) welcome page at once.
      meSays({ loading: true, welcomeSeen: false });
      renderAt('/');
      await act(async () => {
        await new Promise((resolve) => setTimeout(resolve, 50));
      });
      expect(screen.queryByTestId('welcome-screen')).toBeNull();
      expect(document.querySelector('.ant-skeleton')).not.toBeNull();
    });

    it('an employee with an empty menu sees the welcome page first', async () => {
      vi.spyOn(navigationModule, 'useNavigation').mockReturnValue({ items: [], homePath: '/me', loading: false });
      meSays({ roles: ['employee'], welcomeSeen: false });
      renderAt('/');
      expect(await screen.findByTestId('welcome-card-employee')).toBeDefined();
      expect(screen.queryByText('No Modules Available')).toBeNull();
    });

    it('a user with no modules sees NoModules, not the welcome page (spec section 9)', () => {
      vi.spyOn(navigationModule, 'useNavigation').mockReturnValue({ items: [], loading: false });
      meSays({ roles: [], welcomeSeen: false });
      renderAt('/welcome');
      expect(screen.getByText('No Modules Available')).toBeDefined();
      expect(screen.queryByTestId('welcome-screen')).toBeNull();
    });

    it('the user menu opens it again as Getting started', async () => {
      vi.spyOn(navigationModule, 'useNavigation').mockReturnValue(adminFeed);
      meSays({ welcomeSeen: true });
      renderAt('/setup');
      fireEvent.click(screen.getByRole('button', { name: 'User menu' }));
      fireEvent.click(await screen.findByText('Getting started'));
      expect(await screen.findByTestId('welcome-screen')).toBeDefined();
    });
  });
});
