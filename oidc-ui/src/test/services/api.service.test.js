import axios from 'axios';
import { SOMETHING_WENT_WRONG } from '../../constants/routes';
import {
  setupResponseInterceptor,
  HttpError,
  ApiService,
} from '../../services/api.service';

// ✅ Mock sessionStorage
const mockSessionStorage = (() => {
  let store = {};
  return {
    getItem: jest.fn((key) => store[key]),
    setItem: jest.fn((key, val) => (store[key] = val)),
    clear: jest.fn(() => (store = {})),
  };
})();
Object.defineProperty(window, 'sessionStorage', {
  value: mockSessionStorage,
});

// ✅ Mock axios globally
jest.mock('axios', () => {
  const interceptors = {
    request: { use: jest.fn() },
    response: { use: jest.fn(), eject: jest.fn() },
  };
  const mockCreate = jest.fn(() => ({
    interceptors,
    defaults: { baseURL: 'http://localhost/api' },
  }));
  mockCreate.interceptors = interceptors;

  return {
    create: mockCreate,
    get: jest.fn(),
  };
});

describe('ApiService - setupResponseInterceptor and HttpError', () => {
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

  // ✅ Basic sanity check
  it('should create ApiService with correct baseURL', () => {
    expect(ApiService.defaults.baseURL).toBe('http://localhost/api');
  });

  // ✅ Fulfilled path
  it('should return successful response as-is', () => {
    const response = { data: 'OK' };
    expect(fulfilledHandler(response)).toBe(response);
  });

  // ✅ Known HTTP code → navigates
  it('should navigate to error page on known HTTP error', async () => {
    const error = { response: { status: 404 } };
    await rejectedHandler(error);
    expect(mockNavigate).toHaveBeenCalledWith(SOMETHING_WENT_WRONG, {
      state: { code: 404 },
    });
  });

  // ✅ Unknown error code → rejects
  it('should reject and not navigate when status not in known list', async () => {
    const error = { response: { status: 999 }, message: 'Weird error' };
    await expect(rejectedHandler(error)).rejects.toThrow('Weird error');
    expect(mockNavigate).not.toHaveBeenCalled();
  });

  // ✅ HttpError class works
  it('should construct HttpError properly', () => {
    const err = new HttpError('Boom', 503);
    expect(err.message).toBe('Boom');
    expect(err.code).toBe(503);
    expect(err).toBeInstanceOf(Error);
  });
});
