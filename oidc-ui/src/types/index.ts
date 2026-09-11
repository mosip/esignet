export interface ThemeConfig {
  background_logo: boolean;
  footer: boolean;
  [key: string]: boolean;
}

export interface PollingConfig {
  url: string;
  interval: number;
  timeout: number;
  enabled: boolean;
}

export interface CSSImageVariables {
  [key: string]: string;
}
