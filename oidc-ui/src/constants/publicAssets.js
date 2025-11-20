// PUBLIC_URL constant for direct string concatenation
export const PUBLIC_URL = process.env.PUBLIC_URL || '';

/**
 * Image Assets Constants
 * Each key represents an image with its static URL path
 */
const IMAGES = {
  BRAND_LOGO: PUBLIC_URL + '/images/brand_logo.png',
  FOOTER_LOGO: PUBLIC_URL + '/images/footer_logo.png',
  LOGO: PUBLIC_URL + '/logo.png',
  ILLUSTRATION_ONE: PUBLIC_URL + '/images/illustration_one.png',
  SECTION_BG: PUBLIC_URL + '/images/section-bg.png',
  SUBMIT_BG: PUBLIC_URL + '/images/submit_bg.png',
  TOP_LEFT_BG_LOGO: PUBLIC_URL + '/images/top_left_bg_logo.svg',
  BOTTOM_RIGHT_BG_LOGO: PUBLIC_URL + '/images/bottom_right_bg_logo.svg',
  BG_BOTTOM_LEFT: PUBLIC_URL + '/images/bg_bottom_left.png',
  CROSS_ICON: PUBLIC_URL + '/images/cross_icon.svg',
  UP_DOWN_ARROW_ICON: PUBLIC_URL + '/images/up_down_arrow_icon.svg',
  LEFT_ARROW_ICON: PUBLIC_URL + '/images/left_arrow_icon.svg',
  SYNC_ALT_BLACK: PUBLIC_URL + '/images/sync_alt_black.svg',
  ERROR_ICON: PUBLIC_URL + '/images/error_icon.svg',
  INFO_ICON: PUBLIC_URL + '/images/info_icon.svg',
  WARNING_MESSAGE_ICON: PUBLIC_URL + '/images/warning_message_icon.svg',
  CHEVRON_DOWN: PUBLIC_URL + '/images/chevron_down.svg',
  ASTERISK_ICON: PUBLIC_URL + '/images/asterisk_icon.svg',
  BIO_ICON: PUBLIC_URL + '/images/bio_icon.svg',
  FACE_CAPTURE: PUBLIC_URL + '/images/face_capture.png',
  FINGERPRINT_SCAN: PUBLIC_URL + '/images/fingerprint_scan.png',
  IRIS_CODE: PUBLIC_URL + '/images/iris_code.png',
  PHOTO_SCAN: PUBLIC_URL + '/images/photo_scan.png',
  OTP_ICON: PUBLIC_URL + '/images/otp_icon.svg',
  OTP_IMAGE: PUBLIC_URL + '/images/otp_image.png',
  PWD_ICON: PUBLIC_URL + '/images/pwd_icon.svg',
  KBI_ICON: PUBLIC_URL + '/images/kbi_icon.svg',
  SIGN_IN_WITH_FACE: PUBLIC_URL + '/images/Sign in with face.png',
  SIGN_IN_WITH_FINGERPRINT: PUBLIC_URL + '/images/Sign in with fingerprint.png',
  SIGN_IN_WITH_INJI: PUBLIC_URL + '/images/Sign in with Inji.png',
  SIGN_IN_WITH_IRIS: PUBLIC_URL + '/images/Sign in with Iris.png',
  SIGN_IN_WITH_KBI: PUBLIC_URL + '/images/sign_in_with_kbi.png',
  SIGN_IN_WITH_OTP: PUBLIC_URL + '/images/sign_in_with_otp.png',
  EMAIL_ICON: PUBLIC_URL + '/images/email_icon.svg',
  MOBILE_ICON: PUBLIC_URL + '/images/mobile_icon.svg',
  VID_ICON: PUBLIC_URL + '/images/vid_icon.svg',
  NRC_ID_ICON: PUBLIC_URL + '/images/nrc_id_icon.svg',
  IDENTITY_ICON: PUBLIC_URL + '/images/identity_icon.png',
  PASSWORD_HIDE: PUBLIC_URL + '/images/password_hide.svg',
  PASSWORD_SHOW: PUBLIC_URL + '/images/password_show.svg',
  TOGGLE_OFF: PUBLIC_URL + '/images/toggle_off.png',
  TOGGLE_ON: PUBLIC_URL + '/images/toggle_on.png',
  DROP_DOWN: PUBLIC_URL + '/images/drop_down.png',
  DROP_DOWN_ICON: PUBLIC_URL + '/images/drop_down_icon.png',
  SELECT_ICON: PUBLIC_URL + '/images/select_icon.png',
  UNDER_CONSTRUCTION: PUBLIC_URL + '/images/under_construction.svg',
  NO_INTERNET: PUBLIC_URL + '/images/no_internet.svg',
  QR_CODE: PUBLIC_URL + '/images/qr_code.png',
  WALLET_ICON: PUBLIC_URL + '/images/wallet_icon.svg',
  LANGUAGE_ICON: PUBLIC_URL + '/images/language_icon.png',
  REFRESH_LOGO: PUBLIC_URL + '/images/refresh_logo.png',
  EDIT_ACCESS_ICON: PUBLIC_URL + '/images/edit_access_icon.png',
};

/**
 * CSS Variables for Background Images
 * Used by cssVariablesService to inject dynamic CSS variables
 */
export const CSS_IMAGE_VARIABLES = {
  '--brand-only-logo-url': PUBLIC_URL + '/logo.png',
  '--brand-logo-url': PUBLIC_URL + '/images/brand_logo.png',
  '--background-logo-url': PUBLIC_URL + '/images/illustration_one.png',
  '--footer-brand-logo-url': PUBLIC_URL + '/images/footer_logo.png',
  '--top-left-bg-logo-url': PUBLIC_URL + '/images/top_left_bg_logo.svg',
  '--bottom-right-bg-logo-url': PUBLIC_URL + '/images/bottom_right_bg_logo.svg',
  '--bottom-left-bg-logo-url': PUBLIC_URL + '/images/top_left_bg_logo.svg',
  '--top-right-bg-logo-url': PUBLIC_URL + '/images/top_left_bg_logo.svg',
  '--login-background-image': PUBLIC_URL + '/images/section-bg.png',
};

/**
 * Config and Locale File URLs
 * For services that load configuration files
 */
export const CONFIG_URLS = {
  THEME_CONFIG: PUBLIC_URL + '/theme/config.json',
  LOCALE_CONFIG: PUBLIC_URL + '/locales/default.json',
  ENG_CONFIG: PUBLIC_URL + '/locales/en.json',
};

export default IMAGES;
