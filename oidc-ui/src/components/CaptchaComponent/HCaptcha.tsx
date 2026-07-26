import HCaptchaLib from "@hcaptcha/react-hcaptcha";
import type { CaptchaProps } from "./CaptchaModel";

export default function HCaptcha({
  captcha,
  handleSuccess,
  handleError,
  handleExpire,
}: CaptchaProps) {
  const { siteKey, theme, size } = captcha ?? {};

  return (
    <HCaptchaLib
      sitekey={siteKey ?? ""}
      onVerify={handleSuccess}
      onExpire={handleExpire}
      onError={handleError}
      theme={theme ?? "light"}
      size={(size as "normal" | "compact" | "invisible") ?? "normal"}
    />
  );
}
