import {
  FormControl,
  useTranslation,
  validateFieldValue,
} from "@thunderid/react";
import GoogleReCaptcha from "./GoogleReCaptcha";
import CloudflareTurnstile from "./CloudflareTurnstile";
import HCaptcha from "./HCaptcha";
import type { CaptchaComponentProps } from "./CaptchaModel";

// available captcha provider
const availableProvider = [
  "google-recaptcha",
  "cloudflare-turnstile",
  "hcaptcha",
];

export default function CaptchaComponent({
  component,
  context,
}: CaptchaComponentProps) {
  const { t } = useTranslation();
  const fieldRef = component.ref ?? component.id;
  const { formValues, formErrors, touchedFields } = context;
  const isTouched = touchedFields[fieldRef] || false;
  const values = formValues[fieldRef];
  const { provider, siteKey } = component.captcha ?? {};

  const validationError = validateFieldValue(
    values,
    "TEXT" as any,
    component.required,
    isTouched,
  );

  const error = formErrors[fieldRef] || validationError || undefined;

  /**
   * Setting the captcha in the form
   * @param token captcha token got from captcha component
   */
  const handleSuccess = (token: string | null) => {
    context.onInputChange(fieldRef, token ?? "");
  };

  /**
   * Reset the captcha value as soon as it is expire
   */
  const handleExpire = () => {
    context.onInputChange(fieldRef, "");
  };

  /**
   * Reset the captcha values as soon as it encountered error
   */
  const handleError = () => {
    context.onInputChange(fieldRef, "");
  };

  const captchaProps = {
    captcha: component.captcha,
    handleSuccess,
    handleError,
    handleExpire,
  };

  let providerElement = null;

  if (!siteKey || !availableProvider.includes(provider ?? "")) {
    providerElement = <span role="alert">{t("captcha.config.error")}</span>;

    if (context.formValues[fieldRef]) {
      context.onInputChange(fieldRef, "");
    }
    context.formErrors[fieldRef] = t("captcha.config.error");
  } else {
    if (provider === "google-recaptcha") {
      providerElement = <GoogleReCaptcha {...captchaProps} />;
    } else if (provider === "cloudflare-turnstile") {
      providerElement = <CloudflareTurnstile {...captchaProps} />;
    } else if (provider === "hcaptcha") {
      providerElement = <HCaptcha {...captchaProps} />;
    }
  }

  return (
    <>
      {providerElement ? (
        <FormControl error={error}>{providerElement}</FormControl>
      ) : null}
    </>
  );
}
