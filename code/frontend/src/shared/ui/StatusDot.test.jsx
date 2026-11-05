import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ConfigProvider } from 'antd';
import { StatusDot } from './StatusDot.jsx';
import { theme } from '../theme.js';

const wrap = (ui) => render(<ConfigProvider theme={theme}>{ui}</ConfigProvider>);

describe('StatusDot (W-83.7)', () => {
  it('renders the text with a dot before it', () => {
    const { container } = wrap(<StatusDot tone="success">Active</StatusDot>);
    expect(screen.getByText('Active')).toBeDefined();
    expect(container.querySelector('span[aria-hidden="true"]')).not.toBeNull();
  });

  it('colours the dot from the theme token, not a literal', () => {
    const { container } = wrap(<StatusDot tone="error">Terminated</StatusDot>);
    const dot = container.querySelector('span[aria-hidden="true"]');
    // jsdom normalises the hex to rgb(); the value is the theme's colorError.
    expect(dot.style.background).toBe('rgb(225, 29, 72)');
    expect(theme.token.colorError).toBe('#e11d48');
  });

  it('falls back to a quiet dot for an unknown tone', () => {
    const { container } = wrap(<StatusDot>Draft</StatusDot>);
    expect(container.querySelector('span[aria-hidden="true"]').style.background).not.toBe('');
  });
});
