import axios from "axios";

import { SOMETHING_WENT_WRONG, PAGE_NOT_FOUND } from "../constants/routes";

const API_BASE_URL =
  process.env.NODE_ENV === "development"
    ? process.env.REACT_APP_ESIGNET_API_URL
    : window.origin + process.env.REACT_APP_ESIGNET_API_URL;

export class HttpError extends Error {
  code;
  constructor(message, code) {
    super(message);
    this.code = code;
  }
}

// Create own axios instance with defaults.
export const ApiService = axios.create({
  withCredentials: true,
  baseURL: API_BASE_URL,
});

export const setupResponseInterceptor = (navigate) => {
  ApiService.interceptors.response.use(
    (response) => response,
    (error) => {
      const state = { code: error.response.status };
      if (
        error.response?.status &&
        [400, 403, 404, 405, 415, 500, 502, 503, 504].includes(state.code)
      ) {
        if ([400, 403, 404, 405].includes(state.code)) {
          navigate(PAGE_NOT_FOUND, { state });
        } else if ([500, 502, 503, 504].includes(state.code)) {
          navigate(SOMETHING_WENT_WRONG, { state });
        }
      } else {
        return Promise.reject(error);
      }
    }
  );
};
