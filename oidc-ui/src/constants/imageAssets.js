/**
 * Helper function to generate dynamic image URLs
 * @param {string} imagePath - The relative path to the image from public folder
 * @returns {string} Complete URL with PUBLIC_URL prefix
 */
export const getImageUrl = (imagePath) => {
  const publicUrl = process.env.PUBLIC_URL || '';
  return `${publicUrl}${imagePath}`;
};

/**
 * Image Assets Constants
 * Each key represents an image with its dynamic URL
 */
export const IMAGES = {
  BRAND_LOGO: getImageUrl('/images/brand_logo.png'),
  FOOTER_LOGO: getImageUrl('/images/footer_logo.png'),
  LOGO: getImageUrl('/logo.png'),
  ILLUSTRATION_ONE: getImageUrl('/images/illustration_one.png'),
  SECTION_BG: getImageUrl('/images/section-bg.png'),
  SUBMIT_BG: getImageUrl('/images/submit_bg.png'),
  TOP_LEFT_BG_LOGO: getImageUrl('/images/top_left_bg_logo.svg'),
  BOTTOM_RIGHT_BG_LOGO: getImageUrl('/images/bottom_right_bg_logo.svg'),
  BG_BOTTOM_LEFT: getImageUrl('/images/bg_bottom_left.png'),
  CROSS_ICON: getImageUrl('/images/cross_icon.svg'),
  UP_DOWN_ARROW_ICON: getImageUrl('/images/up_down_arrow_icon.svg'),
  LEFT_ARROW_ICON: getImageUrl('/images/left_arrow_icon.svg'),
  SYNC_ALT_BLACK: getImageUrl('/images/sync_alt_black.svg'),
  ERROR_ICON: getImageUrl('/images/error_icon.svg'),
  INFO_ICON: getImageUrl('/images/info_icon.svg'),
  WARNING_MESSAGE_ICON: getImageUrl('/images/warning_message_icon.svg'),
  CHEVRON_DOWN: getImageUrl('/images/chevron_down.svg'),
  ASTERISK_ICON: getImageUrl('/images/asterisk_icon.svg'),
  BIO_ICON: getImageUrl('/images/bio_icon.svg'),
  FACE_CAPTURE: getImageUrl('/images/face_capture.png'),
  FINGERPRINT_SCAN: getImageUrl('/images/fingerprint_scan.png'),
  IRIS_CODE: getImageUrl('/images/iris_code.png'),
  PHOTO_SCAN: getImageUrl('/images/photo_scan.png'),
  OTP_ICON: getImageUrl('/images/otp_icon.svg'),
  OTP_IMAGE: getImageUrl('/images/otp_image.png'),
  PWD_ICON: getImageUrl('/images/pwd_icon.svg'),
  KBI_ICON: getImageUrl('/images/kbi_icon.svg'),
  SIGN_IN_WITH_FACE: getImageUrl('/images/Sign in with face.png'),
  SIGN_IN_WITH_FINGERPRINT: getImageUrl('/images/Sign in with fingerprint.png'),
  SIGN_IN_WITH_INJI: getImageUrl('/images/Sign in with Inji.png'),
  SIGN_IN_WITH_IRIS: getImageUrl('/images/Sign in with Iris.png'),
  SIGN_IN_WITH_KBI: getImageUrl('/images/sign_in_with_kbi.png'),
  SIGN_IN_WITH_OTP: getImageUrl('/images/sign_in_with_otp.png'),
  EMAIL_ICON: getImageUrl('/images/email_icon.svg'),
  MOBILE_ICON: getImageUrl('/images/mobile_icon.svg'),
  VID_ICON: getImageUrl('/images/vid_icon.svg'),
  NRC_ID_ICON: getImageUrl('/images/nrc_id_icon.svg'),
  IDENTITY_ICON: getImageUrl('/images/identity_icon.png'),
  PASSWORD_HIDE: getImageUrl('/images/password_hide.svg'),
  PASSWORD_SHOW: getImageUrl('/images/password_show.svg'),
  TOGGLE_OFF: getImageUrl('/images/toggle_off.png'),
  TOGGLE_ON: getImageUrl('/images/toggle_on.png'),
  DROP_DOWN: getImageUrl('/images/drop_down.png'),
  DROP_DOWN_ICON: getImageUrl('/images/drop_down_icon.png'),
  SELECT_ICON: getImageUrl('/images/select_icon.png'),
  UNDER_CONSTRUCTION: getImageUrl('/images/under_construction.svg'),
  NO_INTERNET: getImageUrl('/images/no_internet.svg'),
  QR_CODE: getImageUrl('/images/qr_code.png'),
  WALLET_ICON: getImageUrl('/images/wallet_icon.svg'),
  LANGUAGE_ICON: getImageUrl('/images/language_icon.png'),
  REFRESH_LOGO: getImageUrl('/images/refresh_logo.png'),
  EDIT_ACCESS_ICON: getImageUrl('/images/edit_access_icon.png'),
};

/**
 * CSS Variables for Background Images
 * Used by cssVariablesService to inject dynamic CSS variables
 */
export const CSS_IMAGE_VARIABLES = {
  '--brand-only-logo-url': getImageUrl('/logo.png'),
  '--brand-logo-url': getImageUrl('/images/brand_logo.png'),
  '--background-logo-url': getImageUrl('/images/illustration_one.png'),
  '--footer-brand-logo-url': getImageUrl('/images/footer_logo.png'),
  '--top-left-bg-logo-url': getImageUrl('/images/top_left_bg_logo.svg'),
  '--bottom-right-bg-logo-url': getImageUrl('/images/bottom_right_bg_logo.svg'),
  '--bottom-left-bg-logo-url': getImageUrl('/images/top_left_bg_logo.svg'),
  '--top-right-bg-logo-url': getImageUrl('/images/top_left_bg_logo.svg'),
  '--login-background-image': getImageUrl('/images/section-bg.png'),
};

/**
 * Config and Locale File URLs
 * For services that load configuration files
 */
export const CONFIG_URLS = {
  THEME_CONFIG: getImageUrl('/theme/config.json'),
  LOCALE_CONFIG: getImageUrl('/locales/default.json'),
  ENG_CONFIG: getImageUrl('/locales/en.json'),
};

export default IMAGES;
