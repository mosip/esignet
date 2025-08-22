import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import Authorize from '../../components/Authorize';

// Mocks
const mockGetCsrfToken = jest.fn();
const mockPostOauthDetails = jest.fn();
const mockBuildRedirectParams = jest.fn();
const mockStoreQueryParam = jest.fn();
const mockNavigate = jest.fn();

jest.mock('react-router-dom', () => {
  const actual = jest.requireActual('react-router-dom');
  return {
    ...actual,
    useNavigate: () => mockNavigate,
    useSearchParams: () => {
      const params = new URLSearchParams(
        'state=testState&nonce=testNonce&claims=%7B%22email%22%3Atrue%7D'
      );
      return [params, jest.fn()];
    },
  };
});

jest.mock('../../common/LoadingIndicator', () => {
  const MockLoading = () => <div>Loading...</div>;
  MockLoading.displayName = 'MockLoadingIndicator';
  return MockLoading;
});

jest.mock('../../common/ErrorIndicator', () => {
  const MockError = ({ errorCode, defaultMsg }) => (
    <div>{`Error: ${errorCode || defaultMsg}`}</div>
  );
  MockError.displayName = 'MockErrorIndicator';
  return MockError;
});

// Helper render
const renderComponent = () => {
  const mockAuthService = {
    get_CsrfToken: mockGetCsrfToken,
    post_OauthDetails_v3: mockPostOauthDetails,
    buildRedirectParams: mockBuildRedirectParams,
    storeQueryParam: mockStoreQueryParam,
  };

  render(
    <MemoryRouter initialEntries={['/authorize']}>
      <Routes>
        <Route
          path="/authorize"
          element={<Authorize authService={mockAuthService} />}
        />
        <Route path="/login" element={<div>Login Page</div>} />
      </Routes>
    </MemoryRouter>
  );
};

beforeEach(() => {
  jest.clearAllMocks();
  process.env.PUBLIC_URL = '';
});

test('renders loading state', async () => {
  mockPostOauthDetails.mockResolvedValue({ response: {}, errors: [] });
  mockBuildRedirectParams.mockReturnValue('?redirect=params');

  renderComponent();
  expect(screen.getByText('Loading...')).toBeInTheDocument();
});

test('handles valid claims and no errors from API', async () => {
  mockPostOauthDetails.mockResolvedValue({
    response: { client_id: 'abc' },
    errors: [],
  });
  mockGetCsrfToken.mockResolvedValue();
  mockBuildRedirectParams.mockReturnValue('?redirect=params');

  renderComponent();

  await waitFor(() => {
    expect(mockNavigate).toHaveBeenCalledWith('/login?redirect=params', {
      replace: true,
    });
  });
});

test('handles error in post_OauthDetails_v3', async () => {
  mockPostOauthDetails.mockResolvedValue({
    response: null,
    errors: [{ errorCode: 'invalid_request', errorMessage: 'Bad request' }],
  });
  mockGetCsrfToken.mockResolvedValue();

  renderComponent();

  await waitFor(() => {
    expect(screen.getByText('Error: invalid_request')).toBeInTheDocument();
  });
});

test('handles network failure in try/catch', async () => {
  mockGetCsrfToken.mockRejectedValue(new Error('Network error'));

  renderComponent();

  await waitFor(() => {
    expect(screen.getByText('Error: Network error')).toBeInTheDocument();
  });
});

test('skips redirect if oAuthDetailResponse.response is null', async () => {
  mockPostOauthDetails.mockResolvedValue({ response: null, errors: [] });
  mockGetCsrfToken.mockResolvedValue();

  renderComponent();

  await waitFor(() => {
    expect(mockNavigate).not.toHaveBeenCalled();
  });
});

test('skips redirect if oAuthDetailResponse has errors', async () => {
  mockPostOauthDetails.mockResolvedValue({
    response: { client_id: 'abc' },
    errors: [{ errorCode: 'authorization_failed' }],
  });
  mockGetCsrfToken.mockResolvedValue();

  renderComponent();

  await waitFor(() => {
    expect(mockNavigate).not.toHaveBeenCalled();
  });
});
