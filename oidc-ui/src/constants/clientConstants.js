const deviceType = {
  face: 'Face',
  finger: 'Finger',
  iris: 'Iris',
};

const challengeTypes = {
  bio: 'BIO',
  pin: 'PIN',
  otp: 'OTP',
  wallet: 'WALLET',
  pswd: 'PWD',
  kbi: 'KBI',
  idt: 'IDT',
};

const challengeFormats = {
  bio: 'encoded-json',
  pin: 'number',
  otp: 'alpha-numeric',
  wallet: 'jwt',
  pswd: 'alpha-numeric',
  kbi: 'base64url-encoded-json',
  idt: 'base64url-encoded-json',
};

const validAuthFactors = {
  PIN: 'PIN',
  OTP: 'OTP',
  BIO: 'BIO',
  PSWD: 'PWD',
  WLA: 'WLA',
  KBI: 'KBI',
  IDT: 'IDT',
};

const buttonTypes = {
  button: 'Button',
  cancel: 'Cancel',
  reset: 'Reset',
  submit: 'Submit',
  discontinue: 'Discontinue',
};

const deepLinkParamPlaceholder = {
  linkCode: 'LINK_CODE',
  linkExpiryDate: 'LINK_EXPIRE_DT',
};

const walletConfigKeys = {
  walletName: 'wallet.name',
  walletLogoUrl: 'wallet.logo-url',
  qrCodeDeepLinkURI: 'wallet.deep-link-uri',
  appDownloadURI: 'wallet.download-uri',
  walletFooter: 'wallet.footer',
};

const configurationKeys = {
  sbiEnv: 'sbi.env',
  sbiPortRange: 'sbi.port.range', //hyphen separated numbers (inclusive). default is 4501-4600
  sbiCAPTURETimeoutInSeconds: 'sbi.timeout.CAPTURE',
  sbiDISCTimeoutInSeconds: 'sbi.timeout.DISC',
  sbiDINFOTimeoutInSeconds: 'sbi.timeout.DINFO',
  sbiFaceCaptureCount: 'sbi.capture.count.face',
  sbiFingerCaptureCount: 'sbi.capture.count.finger',
  sbiIrisCaptureCount: 'sbi.capture.count.iris',
  sbiFaceCaptureScore: 'sbi.capture.score.face',
  sbiFingerCaptureScore: 'sbi.capture.score.finger',
  sbiIrisCaptureScore: 'sbi.capture.score.iris',
  sbiIrisBioSubtypes: 'sbi.bio.subtypes.iris', //comma separated list of bio-subtypes. default is "UNKNOWN"
  sbiFingerBioSubtypes: 'sbi.bio.subtypes.finger', //comma separated list of bio-subtypes. default is "UNKNOWN"

  resendOtpTimeout: 'resend.otp.delay.secs',
  sendOtpChannels: 'send.otp.channels', //comma separated list of otp channels.
  captchaEnableComponents: 'captcha.enable', //comma separated list of components where captcha needs to be shown
  captchaSiteKey: 'captcha.sitekey', //site key for ReCAPTCHA

  linkedTransactionExpireInSecs: 'linked-transaction-expire-in-secs',
  qrCodeBufferInSecs: 'wallet.qr-code-buffer-in-secs',
  authTxnIdLength: 'auth.txnid.length',
  otpLength: 'otp.length',
  pswdRegex: 'password.regex',
  pswdMaxLength: 'password.max-length',
  usernameRegex: 'username.regex',
  usernamePrefix: 'username.prefix',
  usernamePostfix: 'username.postfix',
  usernameMaxLength: 'username.max-length',
  usernameInputType: 'username.input-type',
  consentScreenExpireInSec: 'consent.screen.timeout-in-secs',
  consentScreenTimeOutBufferInSec: 'consent.screen.timeout-buffer-in-secs',
  walletQrCodeAutoRefreshLimit: 'wallet.qr-code.auto-refresh-limit',
  walletConfig: 'wallet.config',
  signupConfig: 'signup.config',
  signupBanner: 'signup.banner',
  signupURL: 'signup.url',
  /** @SuppressWarnings("javascript:s2068") */
  forgotPswdConfig: 'forgot-password.config',
  /** @SuppressWarnings("javascript:s2068") */
  forgotPswd: 'forgot-password',
  /** @SuppressWarnings("javascript:s2068") */
  forgotPswdURL: 'forgot-password.url',
  eKYCStepsConfig: 'eKYC-steps.config',
  bannerCloseTimer: 'error.banner.close-timer',
  authFactorKnowledgeFieldDetails: 'auth.factor.kbi.field-details',
  authFactorKnowledgeIndividualIdField: 'auth.factor.kbi.individual-id-field',
  loginIdOptions: 'login-id.options',
  additionalConfig: 'clientAdditionalConfig',
  signupBannerRequired: 'signup_banner_required',
  /** @SuppressWarnings("javascript:s2068") */
  forgotPswdLinkRequired: 'forgot_pwd_link_required',
};

const modalityIconPath = {
  PIN: 'images/otp_icon.svg',
  OTP: 'images/otp_icon.svg',
  WALLET: 'images/wallet_icon.svg',
  BIO: 'images/bio_icon.svg',
  PSWD: 'images/pwd_icon.svg',
  KBI: 'images/kbi_icon.svg',
};

const errorCodeObj = {
  dismiss: 'consent_rejected',
  invalid_transaction: 'invalid_transaction',
  incompatible_browser: 'incompatible_browser',
  ekyc_failed: 'ekyc_failed',
  no_ekyc_provider: 'no_ekyc_provider',
};

const purposeTitleKey = {
  login: 'login_heading',
  verify: 'verify_heading',
  link: 'link_heading',
};

const purposeSubTitleKey = {
  login: 'login_subheading',
  verify: 'verify_subheading',
  link: 'link_subheading',
};

const authLabelKey = {
  login: 'login_with_id',
  verify: 'verify_with_id',
  link: 'link_using_id',
};

const multipleIdKey = {
  login: 'login_with_id_multiple',
  verify: 'verify_with_id_multiple',
  link: 'link_using_id_multiple',
};

const purposeTypeObj = {
  login: 'login',
  verify: 'verify',
  link: 'link',
  none: 'none',
};

export {
  deviceType,
  challengeTypes,
  configurationKeys,
  validAuthFactors,
  deepLinkParamPlaceholder,
  buttonTypes,
  challengeFormats,
  walletConfigKeys,
  modalityIconPath,
  errorCodeObj,
  purposeTitleKey,
  purposeTypeObj,
  purposeSubTitleKey,
  authLabelKey,
  multipleIdKey,
};
