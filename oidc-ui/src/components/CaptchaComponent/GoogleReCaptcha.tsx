import ReCAPTCHA from "react-google-recaptcha";
import type { CaptchaProps } from "./CaptchaModel";

export default function GoogleReCaptcha({
  captcha,
  handleSuccess,
  handleError,
  handleExpire,
}: CaptchaProps) {
  const { siteKey, theme, size } = captcha ?? {};

  return (
    <ReCAPTCHA
      sitekey={siteKey ?? ""}
      onChange={handleSuccess}
      onExpired={handleExpire}
      onError={handleError}
      theme={theme ?? "light"}
      size={(size as "normal" | "compact" | "invisible") ?? "normal"}
    />
  );
}
