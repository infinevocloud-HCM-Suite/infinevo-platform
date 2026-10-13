import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { StatusTag, renderStatus } from './StatusTag.jsx';

describe('StatusTag', () => {
  it('shows a status code in words', () => {
    render(<StatusTag status="IN_PROGRESS" />);
    expect(screen.getByText('In progress')).toBeTruthy();
  });

  it('shows a dash for a missing status', () => {
    render(<StatusTag status={null} />);
    expect(screen.getByText('—')).toBeTruthy();
  });

  it('renders as a table column cell', () => {
    render(<div>{renderStatus('APPROVED')}</div>);
    expect(screen.getByText('Approved')).toBeTruthy();
  });
});
