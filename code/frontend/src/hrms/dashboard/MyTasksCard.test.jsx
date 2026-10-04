import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { MyTasksCard } from './MyTasksCard.jsx';
import { managerReply } from './dashboardFixture.js';

const renderCard = (tasks, asOf) =>
  render(
    <MemoryRouter>
      <MyTasksCard tasks={tasks} asOf={asOf} />
    </MemoryRouter>,
  );

describe('MyTasksCard (W-48.6 §7)', { timeout: 60000 }, () => {
  it('a task due before as_of is red; one due after is not', () => {
    renderCard(managerReply().me.tasks, '2026-10-04');
    expect(screen.getByTestId('due-k-1').className).toContain('ant-typography-danger');
    expect(screen.getByTestId('due-k-2').className).not.toContain('ant-typography-danger');
  });

  it('uses as_of, not the browser date: the same task is not red when as_of is earlier', () => {
    renderCard(managerReply().me.tasks, '2026-10-01');
    expect(screen.getByTestId('due-k-1').className).not.toContain('ant-typography-danger');
  });

  it('counts are the reply figures, not the rows counted', () => {
    const { container } = renderCard(managerReply().me.tasks, '2026-10-04');
    const values = [...container.querySelectorAll('.ant-statistic-content-value')].map((n) => n.textContent);
    expect(values).toEqual(['9', '3', '2']);
    expect(container.querySelectorAll('tbody tr.ant-table-row')).toHaveLength(2);
  });

  it('renders nothing for a null block', () => {
    const { container } = renderCard(null, '2026-10-04');
    expect(container.innerHTML).toBe('');
  });
});
