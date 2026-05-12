const BASE_URL = import.meta.env.BASE_URL ?? '';

export const IMAGES = {
  BRAND_LOGO: `${BASE_URL}images/brand_logo.png`,
  FOOTER_LOGO: `${BASE_URL}images/footer_logo.png`,
  LOGO: `${BASE_URL}logo.png`,
  ILLUSTRATION_ONE: `${BASE_URL}images/illustration_one.png`,
  SECTION_BG: `${BASE_URL}images/section-bg.png`,
  TOP_LEFT_BG_LOGO: `${BASE_URL}images/top_left_bg_logo.svg`,
  BOTTOM_RIGHT_BG_LOGO: `${BASE_URL}images/bottom_right_bg_logo.svg`,
  CROSS_ICON: `${BASE_URL}images/cross_icon.svg`,
  ERROR_ICON: `${BASE_URL}images/error_icon.svg`,
  INFO_ICON: `${BASE_URL}images/info_icon.svg`,
  WARNING_MESSAGE_ICON: `${BASE_URL}images/warning_message_icon.svg`,
  UNDER_CONSTRUCTION: `${BASE_URL}images/under_construction.svg`,
  NO_INTERNET: `${BASE_URL}images/no_internet.svg`,
  LANGUAGE_ICON: `${BASE_URL}images/language_icon.png`,
  REFRESH_LOGO: `${BASE_URL}images/refresh_logo.png`,
} as const;

export const CSS_IMAGE_VARIABLES: Record<string, string> = {
  '--brand-only-logo-url': `${BASE_URL}logo.png`,
  '--brand-logo-url': `${BASE_URL}images/brand_logo.png`,
  '--background-logo-url': `${BASE_URL}images/illustration_one.png`,
  '--footer-brand-logo-url': `${BASE_URL}images/footer_logo.png`,
  '--top-left-bg-logo-url': `${BASE_URL}images/top_left_bg_logo.svg`,
  '--bottom-right-bg-logo-url': `${BASE_URL}images/bottom_right_bg_logo.svg`,
  '--bottom-left-bg-logo-url': `${BASE_URL}images/top_left_bg_logo.svg`,
  '--top-right-bg-logo-url': `${BASE_URL}images/top_left_bg_logo.svg`,
  '--login-background-image': `${BASE_URL}images/section-bg.png`,
};

export const CONFIG_URLS = {
  THEME_CONFIG: `${BASE_URL}theme/config.json`,
  LOCALE_CONFIG: `${BASE_URL}locales/default.json`,
  ENG_CONFIG: `${BASE_URL}locales/en.json`,
} as const;
