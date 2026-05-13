/// <reference types="vite/client" />

interface EnvConfig {
  DEFAULT_LANG: string;
  DEFAULT_WELLKNOWN: string;
  DEFAULT_THEME: string;
  DEFAULT_FAVICON: string;
  DEFAULT_TITLE: string;
  DEFAULT_ID_PROVIDER_NAME: string;
  DEFAULT_FONT_URL: string;
  POLLING_URL?: string;
  POLLING_INTERVAL?: string;
  POLLING_TIMEOUT?: string;
  POLLING_ENABLED?: string;
}

interface Window {
  _env_: EnvConfig;
}
