import { FormControl, validateFieldValue } from "@thunderid/react";
import GoogleReCaptcha from "./GoogleReCaptcha";
import CloudflareTurnstile from "./CloudflareTurnstile";
import HCaptcha from "./HCaptcha";
import type { CaptchaComponentProps } from "./CaptchaModel";

export default function CaptchaComponent({
  component,
  context,
}: CaptchaComponentProps) {
  const fieldRef = component.ref ?? component.id;
  const { formValues, formErrors, touchedFields } = context;
  const isTouched = touchedFields[fieldRef] || false;
  const values = formValues[fieldRef];
  const { provider } = component.captcha ?? {};

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

  if (provider === "google-recaptcha") {
    providerElement = <GoogleReCaptcha {...captchaProps} />;
  } else if (provider === "cloudflare-turnstile") {
    providerElement = <CloudflareTurnstile {...captchaProps} />;
  } else if (provider === "hcaptcha") {
    providerElement = <HCaptcha {...captchaProps} />;
  } else {
    providerElement = (
      <span role="alert">CAPTCHA provider configuration is invalid.</span>
    );
    context.onInputChange(fieldRef, "");
    context.formErrors[fieldRef] = "CAPTCHA provider configuration is invalid.";
  }

  return (
    <>
      {providerElement ? (
        <FormControl error={error}>{providerElement}</FormControl>
      ) : null}
    </>
  );
}
