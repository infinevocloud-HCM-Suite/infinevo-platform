import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import { PageHeader } from './PageHeader.jsx';

describe('PageHeader component (W-83.1)', () => {
  it('renders title as a level 4 heading', () => {
    render(<PageHeader title="Employee Directory" />);
    const heading = screen.getByRole('heading', { level: 4, name: 'Employee Directory' });
    expect(heading).toBeTruthy();
  });

  it('renders subtitle, count and actions when provided, and omits them when not provided', () => {
    const { rerender } = render(<PageHeader title="Employees" />);
    expect(screen.queryByText('12 employees')).toBeNull();
    expect(screen.queryByText('Manage staff members')).toBeNull();
    expect(screen.queryByText('Create Employee')).toBeNull();

    rerender(
      <PageHeader
        title="Employees"
        subtitle="Manage staff members"
        count="12 employees"
        actions={<button type="button">Create Employee</button>}
      />
    );

    expect(screen.getByText('Manage staff members')).toBeTruthy();
    expect(screen.getByText('12 employees')).toBeTruthy();
    expect(screen.getByText('Create Employee')).toBeTruthy();
  });

  it('renders back button when onBack is passed and triggers callback on click', () => {
    const onBack = vi.fn();
    const { rerender } = render(<PageHeader title="Profile" />);
    expect(screen.queryByRole('button', { name: 'Back' })).toBeNull();

    rerender(<PageHeader title="Profile" onBack={onBack} backLabel="Back to List" />);
    const backBtn = screen.getByRole('button', { name: 'Back to List' });
    expect(backBtn).toBeTruthy();

    fireEvent.click(backBtn);
    expect(onBack).toHaveBeenCalledTimes(1);
  });

  it('outer row sets flexWrap: wrap so actions drop on mobile phones (W-80.1)', () => {
    const { container } = render(<PageHeader title="Dashboard" />);
    const outerRow = container.firstChild;
    expect(outerRow).toBeTruthy();
    expect(outerRow.style.display).toBe('flex');
    expect(outerRow.style.flexWrap).toBe('wrap');
  });
});

