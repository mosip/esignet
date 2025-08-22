import { render, screen } from '@testing-library/react';
import DefaultError from '../../components/DefaultError';
import { useTranslation } from 'react-i18next';

jest.mock('react-i18next', () => ({
  useTranslation: jest.fn(),
}));

jest.mock('../../common/ErrorIndicator', () =>
  jest.fn(() => <div data-testid="error-indicator">ErrorIndicator</div>)
);

describe('DefaultError', () => {
  const mockT = jest.fn((key) => key);

  beforeEach(() => {
    jest.clearAllMocks();
    useTranslation.mockReturnValue({ t: mockT });
  });

  test('renders error page with default props', () => {
    render(
      <DefaultError errorCode="404" backgroundImgPath="/images/404.png" />
    );

    expect(useTranslation).toHaveBeenCalledWith('translation', {
      keyPrefix: 'errors',
    });

    expect(mockT).toHaveBeenCalledWith('backgroud_image_alt');

    const img = screen.getByRole('img');
    expect(img).toHaveAttribute('src', '/images/404.png');
  });

  test('renders with custom i18nKeyPrefix', () => {
    render(
      <DefaultError
        errorCode="401"
        backgroundImgPath="/images/unauthorized.png"
        i18nKeyPrefix="customErrors"
      />
    );

    expect(useTranslation).toHaveBeenCalledWith('translation', {
      keyPrefix: 'customErrors',
    });

    expect(screen.getByRole('img')).toHaveAttribute(
      'src',
      '/images/unauthorized.png'
    );
  });
});
