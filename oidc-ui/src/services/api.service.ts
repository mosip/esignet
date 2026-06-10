import axios from "axios";
import { CSRF, SOMETHING_WENT_WRONG } from "../constants/routes";
import type { NavigateFunction } from "react-router-dom";

const API_BASE_URL = import.meta.env.DEV
  ? (import.meta.env.VITE_API_URL ?? "")
  : window.origin + (import.meta.env.VITE_API_URL ?? "");

const csrfEndpoint = `${API_BASE_URL}${CSRF}`;

export class HttpError extends Error {
  code: number;
  constructor(message: string, code: number) {
    super(message);
    this.code = code;
  }
}

async function getCsrfToken(): Promise<string> {
  const response = await axios.get<{ token: string }>(csrfEndpoint, {
    withCredentials: true,
  });
  return response.data.token;
}

/** Axios instance with CSRF token handling and credentials. */
export const ApiService = axios.create({
  withCredentials: true,
  baseURL: API_BASE_URL,
});

ApiService.interceptors.request.use(
  async (config) => {
    if (config.method?.toLowerCase() === "post") {
      let csrfToken = sessionStorage.getItem("csrfToken");
      if (!csrfToken) {
        try {
          csrfToken = await getCsrfToken();
          if (csrfToken) sessionStorage.setItem("csrfToken", csrfToken);
        } catch (error) {
          console.error("Failed to fetch CSRF token:", error);
          return Promise.reject(new Error("CSRF token unavailable"));
        }
      }
      if (csrfToken) {
        config.headers["X-XSRF-TOKEN"] = csrfToken;
      }
    }
    return config;
  },
  (error) => Promise.reject(error as Error),
);

/**
 * Sets up the response interceptor to navigate to error pages on HTTP errors.
 * Call once at app init with the router's navigate function.
 */
export function setupResponseInterceptor(navigate: NavigateFunction): void {
  ApiService.interceptors.response.use(
    (response) => response,
    (error) => {
      const status = (error as { response?: { status?: number } })?.response
        ?.status;
      if (status && status >= 400) {
        navigate(SOMETHING_WENT_WRONG, { state: { code: status } });
      }
      return Promise.reject(
        error instanceof Error ? error : new Error(String(error)),
      );
    },
  );
}
