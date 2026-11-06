import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MetricCard } from './MetricCard.jsx';

describe('MetricCard', () => {
  it('renders title, value, and subtitle', () => {
    render(<MetricCard title="Total Staff" value="53" subtitle="Active employees" />);
    expect(screen.getByText('Total Staff')).toBeTruthy();
    expect(screen.getByText('53')).toBeTruthy();
    expect(screen.getByText('Active employees')).toBeTruthy();
  });
});

