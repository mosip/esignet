import { useRef, useState, useEffect } from "react";
import {
  type ComponentRenderContext,
  Button,
  useTranslation,
} from "@thunderid/react";
import { CaptchaComponent } from "../index";
import type { ResendOtpFlowComponent } from "./ResendOtpModel";

export default function ResendOtp({
  component,
  context,
}: {
  component: ResendOtpFlowComponent;
  context: ComponentRenderContext;
}) {
  const { t } = useTranslation();
  const onInputChangeRef = useRef(context.onInputChange);
  onInputChangeRef.current = context.onInputChange;

  const [remaining, setRemaining] = useState<number>(0);
  const [formattedTime, setFormattedTime] = useState<string>("00:00");
  const [timeLeft, setTimeLeft] = useState<boolean>(true);
  const [captchaToken, setCaptchaToken] = useState<string>("");
  const [captchaError, setCaptchaError] = useState<boolean>(false);
  const expiresIn = component?.timeLeft ?? 0;

  const captchaId = `${component.id}_captcha`;

  useEffect(() => {
    if (expiresIn <= 0) {
      return undefined;
    }

    setRemaining(expiresIn);

    const interval: any = setInterval(() => {
      setRemaining((prev: number) => {
        if (prev <= 1) {
          clearInterval(interval);
          return 0;
        }
        return prev - 1;
      });
    }, 1000);

    return () => clearInterval(interval);
  }, [expiresIn]);

  useEffect(() => {
    setTimeLeft(remaining > 0);
    setFormattedTime(formatTime(remaining));
  }, [remaining]);

  const formatTime = (seconds: number): string => {
    if (seconds <= 0) {
      return t("app.otp.timed_out");
    }
    const m: number = Math.floor(seconds / 60);
    const s: number = seconds % 60;
    return `${m}:${s.toString().padStart(2, "0")}`;
  };

  // to check whether captcha is clicked, expired or get error
  const captchaChanged = (token: string | null | undefined) => {
    const temp = typeof token === "string" && token.trim().length > 0;
    setCaptchaError(!temp);
    setCaptchaToken(token ?? "");
  };

  const handleClick = () => {
    if (context.onSubmit) {
      // resetting the form so nothing
      context.resetForm && context.resetForm();

      // checking whether captcha has been checked or not
      if (component.captcha && !captchaToken) {
        // setting captcha error true, if there is no
        // captcha token present while submitting
        setCaptchaError(true);
        context.onInputChange(captchaId, "");
        return;
      }
      const payload = {
        ...(component.captcha ? { captcha_token: captchaToken } : {}),
      };

      context.onSubmit(component, payload, true);
    }
  };

  return (
    <div className="flex flex-col items-center">
      {timeLeft && (
        <h6 className="thunderid-typography thunderid-typography__h6 pb-2">
          {t("app.otp.resend_timer")} {formattedTime}
        </h6>
      )}

      {!timeLeft && component.captcha && (
        <div className="pb-4">
          <CaptchaComponent
            component={{ ...component, ref: captchaId }}
            context={context}
            key={captchaId}
            captchaChanged={captchaChanged}
          ></CaptchaComponent>
          {captchaError && (
            <div className="text-red-500 text-xs pt-1">
              {t("validations.required.field.error")}
            </div>
          )}
        </div>
      )}

      <Button
        fullWidth
        id={component.id}
        key={component.id}
        onClick={handleClick}
        disabled={timeLeft || captchaError}
        data-testid="thunderid-resend-otp-button"
        variant={
          component.variant?.toLowerCase() === "primary" ? "solid" : "outline"
        }
        color={
          component.variant?.toLowerCase() === "primary"
            ? "primary"
            : "secondary"
        }
        type="button"
      >
        {t(component?.label ?? "otp.resend_otp", {
          defaultValue: t("otp.resend_otp"),
        })}
      </Button>
    </div>
  );
}
