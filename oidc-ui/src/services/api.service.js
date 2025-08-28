import axios from 'axios';

import { CSRF, SOMETHING_WENT_WRONG } from '../constants/routes';

const API_BASE_URL =
  process.env.NODE_ENV === 'development'
    ? process.env.REACT_APP_ESIGNET_API_URL
    : window.origin + process.env.REACT_APP_ESIGNET_API_URL;

const csrfEndpoint = `${API_BASE_URL}${CSRF}`;

export class HttpError extends Error {
  code;
  constructor(message, code) {
    super(message);
    this.code = code;
  }
}

const get_CsrfToken = async () => {
  let response = await axios.get(csrfEndpoint);
  return response.data.token;
};

const allErrorStatusCodes = [
  400, 401, 402, 403, 404, 405, 406, 407, 408, 409, 410, 411, 412, 413, 414,
  415, 416, 417, 418, 421, 422, 423, 424, 425, 426, 428, 429, 431, 451, 500,
  501, 502, 503, 504, 505, 506, 507, 508, 510, 511,
];

// Create own axios instance with defaults.
export const ApiService = axios.create({
  withCredentials: true,
  baseURL: API_BASE_URL,
});

ApiService.interceptors.request.use(
  async (config) => {
    try {
      if (config.method?.toLowerCase() === 'post') {
        let csrfToken = sessionStorage.getItem('csrfToken');
        if (!csrfToken) {
          csrfToken = await get_CsrfToken();
          if (csrfToken) sessionStorage.setItem('csrfToken', csrfToken);
        }
        config.headers['X-XSRF-TOKEN'] = csrfToken;
      }
    } catch (error) {
      console.error(error);
      throw error;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

export const setupResponseInterceptor = (navigate) => {
  ApiService.interceptors.response.use(
    (response) => response,
    (error) => {
      const state = { code: error.response.status };
      if (error?.response?.status && allErrorStatusCodes.includes(state.code)) {
        navigate(SOMETHING_WENT_WRONG, { state });
      } else {
        const message = error?.message || 'Unknown error occurred';
        const rejection = error instanceof Error ? error : new Error(message);
        return Promise.reject(rejection);
      }
    }
  );
};
