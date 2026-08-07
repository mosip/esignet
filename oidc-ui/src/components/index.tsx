import SbiCustomRenderer from "./SbiComponent/SbiCustomRenderer";
import ResendOtpRenderer from "./ResendOtpComponent/ResendOtpRenderer";
import BackButtonRenderer from "./BackButtonComponent/BackButtonRenderer";
import CaptchaRenderer from "./CaptchaComponent/CaptchaRenderer";

import SbiComponent from "./SbiComponent/SbiComponent";
import ResendOtpComponent from "./ResendOtpComponent/ResendOtpComponent";
import BackButtonComponent from "./BackButtonComponent/BackButtonComponent";
import CaptchaComponent from "./CaptchaComponent/CaptchaComponent";

import GoogleReCaptcha from "./CaptchaComponent/GoogleReCaptcha";
import HCaptcha from "./CaptchaComponent/HCaptcha";
import CloudflareTurnstile from "./CaptchaComponent/CloudflareTurnstile";

export {
  // export all renderers
  SbiCustomRenderer,
  ResendOtpRenderer,
  BackButtonRenderer,
  CaptchaRenderer,

  // export all components
  SbiComponent,
  ResendOtpComponent,
  BackButtonComponent,
  CaptchaComponent,

  // export all captcha providers
  GoogleReCaptcha,
  HCaptcha,
  CloudflareTurnstile,
};

// export type from captcha model
export type {
  CaptchaFlowComponent,
  CaptchaProvider,
  CaptchaComponentProps,
  CaptchaProps,
  CaptchaComponentType,
} from "./CaptchaComponent/CaptchaModel";

// export type from resend otp model
export type { ResendOtpFlowComponent } from "./ResendOtpComponent/ResendOtpModel";
