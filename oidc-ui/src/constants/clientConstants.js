const deviceType = {
  face: "Face",
  finger: "Finger",
  iris: "Iris",
};

const challengeTypes = {
  bio: "BIO",
  pin: "PIN",
  otp: "OTP",
  wallet: "WALLET",
  pwd: "PWD",
};

const challengeFormats = {
  bio: "encoded-json",
  pin: "number",
  otp: "alpha-numeric",
  wallet: "jwt",
  pwd: "alpha-numeric",
};

const validAuthFactors = {
  PIN: "PIN",
  OTP: "OTP",
  BIO: "BIO",
  PWD: "PWD",
  WLA: "WLA",
  KBI: "KBI"
};

const buttonTypes = {
  button: "Button",
  cancel: "Cancel",
  reset: "Reset",
  submit: "Submit",
  discontinue: "Discontinue"
};

const deepLinkParamPlaceholder = {
  linkCode: "LINK_CODE",
  linkExpiryDate: "LINK_EXPIRE_DT",
};

const walletConfigKeys = {
  walletName: "wallet.name",
  walletLogoUrl: "wallet.logo-url",
  qrCodeDeepLinkURI: "wallet.deep-link-uri",
  appDownloadURI: "wallet.download-uri",
};

const configurationKeys = {
  sbiEnv: "sbi.env",
  sbiPortRange: "sbi.port.range", //hyphen separated numbers (inclusive). default is 4501-4600
  sbiCAPTURETimeoutInSeconds: "sbi.timeout.CAPTURE",
  sbiDISCTimeoutInSeconds: "sbi.timeout.DISC",
  sbiDINFOTimeoutInSeconds: "sbi.timeout.DINFO",
  sbiFaceCaptureCount: "sbi.capture.count.face",
  sbiFingerCaptureCount: "sbi.capture.count.finger",
  sbiIrisCaptureCount: "sbi.capture.count.iris",
  sbiFaceCaptureScore: "sbi.capture.score.face",
  sbiFingerCaptureScore: "sbi.capture.score.finger",
  sbiIrisCaptureScore: "sbi.capture.score.iris",
  sbiIrisBioSubtypes: "sbi.bio.subtypes.iris", //comma separated list of bio-subtypes. default is "UNKNOWN"
  sbiFingerBioSubtypes: "sbi.bio.subtypes.finger", //comma separated list of bio-subtypes. default is "UNKNOWN"

  resendOtpTimeout: "resend.otp.delay.secs",
  sendOtpChannels: "send.otp.channels", //comma separated list of otp channels.
  captchaEnableComponents: "captcha.enable", //comma separated list of components where captcha needs to be shown
  captchaSiteKey: "captcha.sitekey", //site key for ReCAPTCHA

  linkedTransactionExpireInSecs: "linked-transaction-expire-in-secs",
  qrCodeBufferInSecs: "wallet.qr-code-buffer-in-secs",
  authTxnIdLength: "auth.txnid.length",
  otpLength: "otp.length",
  passwordRegex: "password.regex",
  passwordMaxLength: "password.max-length",
  usernameRegex: "username.regex",
  usernamePrefix: "username.prefix",
  usernamePostfix: "username.postfix",
  usernameMaxLength: "username.max-length",
  usernameInputType: "username.input-type",
  consentScreenExpireInSec: "consent.screen.timeout-in-secs",
  consentScreenTimeOutBufferInSec: "consent.screen.timeout-buffer-in-secs",
  walletQrCodeAutoRefreshLimit: "wallet.qr-code.auto-refresh-limit",
  walletConfig: "wallet.config",
  signupConfig: "signup.config",
  signupBanner: "signup.banner",
  signupURL: "signup.url",
  forgotPasswordConfig: "forgot-password.config",
  forgotPassword: "forgot-password",
  forgotPasswordURL: "forgot-password.url",
  eKYCStepsConfig: "eKYC-steps.config",
  bannerCloseTimer: "error.banner.close-timer",
  authFactorKnowledgeFieldDetails: "auth.factor.kbi.field-details",
  authFactorKnowledgeIndividualIdField: "auth.factor.kbi.individual-id-field"
};

const modalityIconPath = {
  PIN: "images/otp_icon.svg",
  OTP: "images/otp_icon.svg",
  WALLET: "images/wallet_icon.svg",
  BIO: "images/bio_icon.svg",
  PWD: "images/sign_in_with_otp.png",
  KBI: "images/sign_in_with_kba.png"
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
};
