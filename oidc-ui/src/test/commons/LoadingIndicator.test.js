import React from 'react';
import { render } from '@testing-library/react';
import LoadingIndicator from '../../common/LoadingIndicator';

jest.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key) => key }),
}));

describe('LoadingIndicator Component', () => {
  it('renders with default props', () => {
    const { getByRole } = render(<LoadingIndicator />);
    const loadingIndicator = getByRole('status');
    expect(loadingIndicator).toBeInTheDocument();
    expect(loadingIndicator).toHaveClass('loading-indicator');
  });

  it('renders with custom message and size', () => {
    const { getByRole, getByText } = render(
      <LoadingIndicator message="custom.loading.message" size="small" />
    );
    const loadingIndicator = getByRole('status');
    const messageElement = getByText('custom.loading.message');
    expect(loadingIndicator).toBeInTheDocument();
    expect(loadingIndicator).toHaveClass('loading-indicator');
    expect(messageElement).toBeInTheDocument();
  });
});
