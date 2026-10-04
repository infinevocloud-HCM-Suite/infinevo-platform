import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { ApprovalsCard } from './ApprovalsCard.jsx';
import { managerReply } from './dashboardFixture.js';

describe('ApprovalsCard (W-48.6 §7)', { timeout: 60000 }, () => {
  it('each row links to its entry view; the count is the reply waiting figure', () => {
    const { container } = render(
      <MemoryRouter>
        <ApprovalsCard approvals={managerReply().team.approvals} />
      </MemoryRouter>,
    );
    expect(screen.getByRole('link', { name: /Asha Rao/ }).getAttribute('href')).toBe(
      '/hrms/timesheet-review/entries/e-1',
    );
    expect(screen.getByRole('link', { name: /Ravi Sen/ }).getAttribute('href')).toBe(
      '/hrms/timesheet-review/entries/e-2',
    );
    expect(screen.getByRole('link', { name: 'Go to approvals' }).getAttribute('href')).toBe('/approvals');
    expect(container.querySelector('.ant-statistic-content-value').textContent).toBe('11');
  });
});
