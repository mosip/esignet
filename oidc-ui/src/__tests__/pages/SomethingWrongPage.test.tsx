import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import SomethingWrongPage from '../../pages/SomethingWrongPage';

describe('SomethingWrongPage', () => {
  it('renders the error image', () => {
    render(
      <MemoryRouter>
        <SomethingWrongPage />
      </MemoryRouter>,
    );
    expect(screen.getByAltText('something_went_wrong')).toBeDefined();
  });

  it('renders the error heading', () => {
    render(
      <MemoryRouter>
        <SomethingWrongPage />
      </MemoryRouter>,
    );
    expect(screen.getByText(/Something went wrong/)).toBeDefined();
  });

  it('renders the error detail', () => {
    render(
      <MemoryRouter>
        <SomethingWrongPage />
      </MemoryRouter>,
    );
    expect(
      screen.getByText('An unexpected error occurred. Please try again later.'),
    ).toBeDefined();
  });

  it('displays the status code from location state', () => {
    render(
      <MemoryRouter
        initialEntries={[
          { pathname: '/something-went-wrong', state: { code: 500 } },
        ]}
      >
        <SomethingWrongPage />
      </MemoryRouter>,
    );
    expect(screen.getByText(/500/)).toBeDefined();
  });
});
