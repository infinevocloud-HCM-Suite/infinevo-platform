import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, act } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import fs from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { Provider } from 'react-redux';
import { store } from './store.js';
import { AppShell } from './AppShell.jsx';
import * as navigationModule from './navigation/useNavigation.js';
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
});
