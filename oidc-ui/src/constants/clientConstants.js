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
  pwd: "PWD"
};

const challengeFormats = {
  bio: "encoded-json",
  pin: "number",
  otp: "alpha-numeric",
  wallet: "jwt",
  pwd: "alpha-numeric"
};


const validAuthFactors = {
  PIN: "PIN",
  OTP: "OTP",
  BIO: "BIO",
  PWD: "PWD"
};

const buttonTypes = {
  button: "Button",
  cancel: "Cancel",
  reset: "Reset",
  submit: "Submit",
};

const deepLinkParamPlaceholder = {
  linkCode: "LINK_CODE",
  linkExpiryDate: "LINK_EXPIRE_DT",
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

  linkCodeExpireInSec: "mosip.esignet.link-code-expire-in-secs",
  linkCodeDeferredTimeoutInSec: "mosip.esignet.link-status-deferred-response-timeout-secs",
  qrCodeDeepLinkURI: "mosip.esignet.qr-code.deep-link-uri",
  appDownloadURI: "mosip.esignet.qr-code.download-uri",
  signInWithQRCodeEnable: "mosip.esignet.qr-code.enable",
  authTxnIdLength: "auth.txnid.length",
  otpLength: "otp.length",
  passwordRegex : "password.regex"
};

export {
  deviceType,
  challengeTypes,
  configurationKeys,
  validAuthFactors,
  deepLinkParamPlaceholder,
  buttonTypes,
  challengeFormats
};
