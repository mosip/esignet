export interface ThemeConfig {
  otp_info_icon: boolean;
  biometrics_info_icon: boolean;
  pin_info_icon: boolean;
  username_info_icon: boolean;
  background_logo: boolean;
  footer: boolean;
  remove_language_indicator_pipe: boolean;
  outline_toggle: boolean;
  outline_dropdown: boolean;
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
