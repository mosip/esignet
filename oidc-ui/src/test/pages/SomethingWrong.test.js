import React from 'react';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, useLocation } from 'react-router-dom';
import SomethingWrongPage from '../../pages/SomethingWrong';

// ✅ Mock react-router-dom's useLocation
jest.mock('react-router-dom', () => {
  const originalModule = jest.requireActual('react-router-dom');
  return {
    ...originalModule,
    useLocation: jest.fn(),
  };
});

// ✅ Mock i18n
jest.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key) => `translated_${key}`,
  }),
}));

describe('SomethingWrongPage', () => {
  it('renders the correct error message and image', () => {
    const mockedCode = '500';
    useLocation.mockReturnValue({
      state: { code: mockedCode },
    });

    render(
      <MemoryRouter>
        <SomethingWrongPage />
      </MemoryRouter>
    );

    // Assert image is present
    expect(screen.getByAltText('something_went_wrong')).toBeInTheDocument();

    // Assert translated texts
    expect(
      screen.getByText(`translated_statusCodeHeader.${mockedCode}`)
    ).toBeInTheDocument();
    expect(
      screen.getByText(`translated_statusCodeSubHeader.${mockedCode}`)
    ).toBeInTheDocument();
  });
});
