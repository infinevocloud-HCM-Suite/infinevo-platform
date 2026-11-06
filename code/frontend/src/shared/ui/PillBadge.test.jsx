import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { PillBadge } from './PillBadge.jsx';

describe('PillBadge', () => {
  it('renders children with default neutral variant', () => {
    render(<PillBadge>Active</PillBadge>);
    expect(screen.getByText('Active')).toBeTruthy();
  });

  it('applies variant classes correctly', () => {
    const { container } = render(<PillBadge variant="success">Approved</PillBadge>);
    expect(container.firstChild.classList.contains('pill-success')).toBe(true);
  });

  it('renders dot when dot is true', () => {
    const { container } = render(<PillBadge variant="warning" dot>Pending</PillBadge>);
    const dotSpan = container.querySelector('span[aria-hidden="true"]');
    expect(dotSpan).toBeTruthy();
  });
});

