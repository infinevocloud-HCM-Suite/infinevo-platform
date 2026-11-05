import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MoneyText } from './MoneyText.jsx';
import { formatMoney, EMPTY } from './format.js';

describe('MoneyText component (W-83.1)', () => {
  it('renders exact same text as formatMoney for numeric values', () => {
    const val = '125000.00';
    render(<MoneyText value={val} />);
    const expected = formatMoney(val);
    expect(screen.getByText(expected)).toBeTruthy();
  });

  it('renders exact same text as formatMoney for numbers and zero', () => {
    const { rerender } = render(<MoneyText value={45000} />);
    expect(screen.getByText(formatMoney(45000))).toBeTruthy();

    rerender(<MoneyText value={0} />);
    expect(screen.getByText(formatMoney(0))).toBeTruthy();
  });

  it('renders exact same text as formatMoney for empty, null, or blank values', () => {
    const { rerender } = render(<MoneyText value={null} />);
    expect(screen.getByText(EMPTY)).toBeTruthy();

    rerender(<MoneyText value={undefined} />);
    expect(screen.getByText(EMPTY)).toBeTruthy();

    rerender(<MoneyText value="" />);
    expect(screen.getByText(EMPTY)).toBeTruthy();
  });

  it('applies tabular-nums fontVariantNumeric styling for column alignment', () => {
    const { container } = render(<MoneyText value="999.00" />);
    const span = container.querySelector('span');
    expect(span).toBeTruthy();
    expect(span.style.fontVariantNumeric).toBe('tabular-nums');
  });

  it('supports custom currency symbols and style/className forwarding', () => {
    const { container } = render(
      <MoneyText value="100.00" symbol="$" className="custom-money" style={{ color: 'inherit' }} />
    );
    expect(screen.getByText(formatMoney('100.00', '$'))).toBeTruthy();
    const span = container.querySelector('span');
    expect(span.className).toContain('custom-money');
    expect(span.style.color).toBe('inherit');
  });
});

