import type { PollingConfig } from "../types";

/**
 * Parses a value as a positive integer, returning a default if invalid.
 */
export function parsePositiveInt(
  value: string | number | undefined,
  defaultValue: number,
): number {
  const num = Number(value);
  return Number.isFinite(num) && num > 0 ? num : defaultValue;
}

/**
 * Returns true if the value is a non-empty, non-whitespace string.
 */
export function isNonEmptyString(value: unknown): value is string {
  return typeof value === "string" && value.trim().length > 0;
}

/**
 * Checks if a property exists in a config object.
 */
export function checkConfigProperty(
  config: Record<string, unknown> | null | undefined,
  property: string,
): boolean {
  return config != null && property in config;
}

/**
 * Gets the polling configuration from window._env_ or defaults.
 */
export function getPollingConfig(): PollingConfig {
  const env = window._env_ ?? ({} as Partial<Window["_env_"]>);
  const { POLLING_URL, POLLING_INTERVAL, POLLING_TIMEOUT, POLLING_ENABLED } =
    env;

  const url =
    POLLING_URL ??
    (import.meta.env.DEV
      ? `${import.meta.env.VITE_API_URL ?? ""}/actuator/health`
      : `${window.origin}/v1/esignet/actuator/health`);

  const interval = parsePositiveInt(POLLING_INTERVAL, 10000);
  const timeout = parsePositiveInt(POLLING_TIMEOUT, 5000);

  const enabled =
    POLLING_ENABLED === undefined || POLLING_ENABLED === ""
      ? true
      : String(POLLING_ENABLED).toLowerCase() === "true";

  return { url, interval, timeout, enabled };
}
