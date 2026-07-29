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

  const handleClick = () => {
    if (context.onSubmit) {
      // checking whether captcha has been checked or not
      context.touchedFields[captchaId] = true;
      if (!context.formValues[captchaId]) {
        // if it is not checked, make the value not,
        // so that it will throw error in ui
        context.onInputChange(captchaId, "");
        return;
      }
      const payload = {
        ...context.formValues,
        captcha_token: context.formValues[captchaId],
      };

      context.onSubmit(component, payload, true);
    }
  };

  return (
    <div className="flex flex-col items-center">
      {timeLeft && (
        <h6 className="thunderid-typography thunderid-typography__h6">
          {t("app.otp.resend_timer")} {formattedTime}
        </h6>
      )}

      {!timeLeft && component.captcha && (
        <>
          <CaptchaComponent
            component={{ ...component, ref: captchaId }}
            context={context}
            key={captchaId}
          ></CaptchaComponent>
          {context.formErrors[captchaId] && (
            <div style={{ color: "red", fontSize: "10px" }}>
              {context.formErrors[captchaId]}
            </div>
          )}
        </>
      )}

      <Button
        fullWidth
        id={component.id}
        key={component.id}
        onClick={handleClick}
        disabled={timeLeft}
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
