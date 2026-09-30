import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { Header } from './Header.jsx';
import * as keycloakModule from './auth/keycloak.js';

describe('Header component', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('displays user name from keycloak.tokenParsed', () => {
    keycloakModule.keycloak.tokenParsed = {
      name: 'Alice Cooper',
      preferred_username: 'acooper',
    };

    render(<Header />);
    expect(screen.getByText('Alice Cooper')).toBeDefined();
  });

  it('falls back to preferred_username when name is missing', () => {
    keycloakModule.keycloak.tokenParsed = {
      preferred_username: 'birengit',
    };

    render(<Header />);
    expect(screen.getByText('birengit')).toBeDefined();
  });

  it('calls logout() when logout button is clicked', () => {
    keycloakModule.keycloak.tokenParsed = {
      name: 'Alice Cooper',
    };
    const logoutSpy = vi.spyOn(keycloakModule, 'logout').mockImplementation(() => {});

    render(<Header />);
    const logoutButton = screen.getByRole('button', { name: /logout/i });
    fireEvent.click(logoutButton);

    expect(logoutSpy).toHaveBeenCalledTimes(1);
  });
});
