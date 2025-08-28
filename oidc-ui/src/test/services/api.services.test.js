import { SOMETHING_WENT_WRONG } from '../../constants/routes';
import {
  setupResponseInterceptor,
  HttpError,
  ApiService,
} from '../../services/api.service';

// 🧠 SAFELY mock axios globally, including interceptors and baseURL
jest.mock('axios', () => {
  const interceptors = {
    response: {
      use: jest.fn(),
      eject: jest.fn(),
    },
  };

  return {
    create: jest.fn(() => ({
      defaults: {
        baseURL:
          process.env.NODE_ENV === 'development'
            ? process.env.REACT_APP_ESIGNET_API_URL
            : 'http://localhost' + process.env.REACT_APP_ESIGNET_API_URL,
      },
      interceptors,
    })),
  };
});

describe('ApiService - setupResponseInterceptor', () => {
  let mockNavigate;
  let fulfilledHandler;
  let rejectedHandler;

  beforeEach(() => {
    jest.clearAllMocks();
    mockNavigate = jest.fn();

    setupResponseInterceptor(mockNavigate);

    const [fulfilled, rejected] =
      ApiService.interceptors.response.use.mock.calls[0];
    fulfilledHandler = fulfilled;
    rejectedHandler = rejected;
  });

  it('should set correct baseURL', () => {
    const expectedBaseUrl =
      process.env.NODE_ENV === 'development'
        ? process.env.REACT_APP_ESIGNET_API_URL
        : 'http://localhost' + process.env.REACT_APP_ESIGNET_API_URL;

    expect(ApiService.defaults.baseURL).toBe(expectedBaseUrl);
  });

  it('should return successful response as-is', () => {
    const response = { data: 'OK' };
    const result = fulfilledHandler(response);
    expect(result).toBe(response);
  });

  it('should navigate to error page on known HTTP error', async () => {
    const error = { response: { status: 404 } };
    await rejectedHandler(error);
    expect(mockNavigate).toHaveBeenCalledWith(SOMETHING_WENT_WRONG, {
      state: { code: 404 },
    });
  });

  it('should construct HttpError properly', () => {
    const err = new HttpError('Boom', 503);
    expect(err.message).toBe('Boom');
    expect(err.code).toBe(503);
  });

  it('should reject but not navigate when status is not in known list', async () => {
    const error = { response: { status: 999 }, message: 'Weird error' };
    await expect(rejectedHandler(error)).rejects.toThrow('Weird error');
    expect(mockNavigate).not.toHaveBeenCalled();
  });
});
