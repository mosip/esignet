import { Turnstile } from "@marsidev/react-turnstile";
import type { CaptchaProps } from "./CaptchaModel";

export default function CloudflareTurnstile({
  captcha,
  handleSuccess,
  handleError,
  handleExpire,
}: CaptchaProps) {
  const { siteKey, theme, size } = captcha ?? {};

  return (
    <Turnstile
      siteKey={siteKey ?? ""}
      onSuccess={handleSuccess}
      onError={handleError}
      onExpire={handleExpire}
      options={{
        theme: theme ?? "light",
        size: (size as "normal" | "compact" | "flexible") ?? "normal",
      }}
    />
  );
}
