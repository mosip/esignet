import { useEffect, useRef, useState } from "react";
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
  const formError = formErrors[fieldRef];
  const isTouched = touchedFields[fieldRef] || false;
  const values = formValues[fieldRef];
  const [error, setError] = useState<string | undefined>(formError);
  const { provider } = component.captcha ?? {};

  const onInputChangeRef = useRef(context.onInputChange);
  onInputChangeRef.current = context.onInputChange;

  /**
   * Setting the captcha in the form
   * @param token captcha token got from captcha component
   */
  const handleSuccess = (token: string | null) => {
    onInputChangeRef.current(fieldRef, token ?? "");
  };

  /**
   * Reset the captcha value as soon as it is expire
   */
  const handleExpire = () => {
    onInputChangeRef.current(fieldRef, "");
  };

  /**
   * Reset the captcha values as soon as it encoutered error
   */
  const handleError = () => {
    onInputChangeRef.current(fieldRef, "");
  };

  const captchaProps = {
    captcha: component.captcha,
    handleSuccess,
    handleError,
    handleExpire,
  };

  let currentProvider = null;

  if (provider === "google-recaptcha") {
    currentProvider = () => <GoogleReCaptcha {...captchaProps} />;
  }

  if (provider === "cloudflare-turnstile") {
    currentProvider = () => <CloudflareTurnstile {...captchaProps} />;
  }

  if (provider === "hcaptcha") {
    currentProvider = () => <HCaptcha {...captchaProps} />;
  }

  /**
   * it will trigger whenever captcha component
   * is touched or its values is changed
   */
  useEffect(() => {
    // validating the captcha value
    const temp = validateFieldValue(
      values,
      "TEXT" as any,
      component.required,
      isTouched,
    );

    setError(temp || undefined);
  }, [isTouched, values]);

  return (
    <>
      {currentProvider ? (
        <FormControl error={error}>{currentProvider()}</FormControl>
      ) : null}
    </>
  );
}
