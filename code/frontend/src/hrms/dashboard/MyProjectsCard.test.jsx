import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { MyProjectsCard } from './MyProjectsCard.jsx';

const projects = {
  active: 1,
  items: [{ project_id: 'p-1', name: 'Apollo', status: 'STARTED', progress: 40, end_date: '2026-12-31' }],
};

describe('MyProjectsCard', { timeout: 60000 }, () => {
  it('D-72: a project links to My work, which every employee reaches, not the manager-only project page', () => {
    render(
      <MemoryRouter>
        <MyProjectsCard projects={projects} />
      </MemoryRouter>,
    );
    expect(screen.getByRole('link', { name: 'Apollo' }).getAttribute('href')).toBe('/hrms/my-work');
  });
});
