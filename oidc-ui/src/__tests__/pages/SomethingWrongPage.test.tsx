import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import SomethingWrongPage from '../../pages/SomethingWrongPage';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string, fallback: string) => fallback || key,
    i18n: { language: 'en', changeLanguage: vi.fn() },
  }),
}));

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
    expect(screen.getByText('Something went wrong')).toBeDefined();
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

  it('uses status code from location state', () => {
    render(
      <MemoryRouter
        initialEntries={[
          { pathname: '/something-went-wrong', state: { code: 500 } },
        ]}
      >
        <SomethingWrongPage />
      </MemoryRouter>,
    );
    // Component renders - state is consumed without error
    expect(screen.getByAltText('something_went_wrong')).toBeDefined();
  });
});
