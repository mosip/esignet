import { useRef, useState, useEffect } from "react";
import { type ComponentRenderContext, useTranslation } from "@thunderid/react";
import { CaptchaComponent } from "../index";
import type { ResendOtpFlowComponent } from "./ResendOtpModel";

export default function ResendOtpComponent({
  component,
  context,
}: {
  component: ResendOtpFlowComponent;
  context: ComponentRenderContext;
}) {
  const { t } = useTranslation();
  const captchaId = `${component.id}_captcha`;

  // timeLeft and attemptsLeft come from the backend (OTP executor additionalData).
  const additionalData = context.additionalData ?? {};
  const expiresIn = Number(additionalData.timeLeft ?? 0) || 0;
  const attemptsLeft = Number(additionalData.attemptsLeft ?? 1);
  const noAttemptsLeft = Number.isFinite(attemptsLeft) && attemptsLeft <= 0;

  // The countdown is derived from a deadline rather than a ticking counter, so
  // restarting it is a single state update and never drifts.
  const [deadline, setDeadline] = useState<number>(
    () => Date.now() + expiresIn * 1000,
  );
  const [now, setNow] = useState<number>(() => Date.now());
  const [showCaptcha, setShowCaptcha] = useState<boolean>(false);
  const [captchaError, setCaptchaError] = useState<boolean>(false);
  // Disables the resend action while the request is in flight.
  const [submitting, setSubmitting] = useState<boolean>(false);
  // Synchronous guard; state updates land too late to stop a repeated captcha callback.
  const sendingRef = useRef<boolean>(false);

  // A resend returns the same timeLeft but one fewer attempt, so both values
  // together identify a fresh countdown.
  const countdownKey = `${expiresIn}|${attemptsLeft}`;
  const [prevCountdownKey, setPrevCountdownKey] =
    useState<string>(countdownKey);
  const [prevLoading, setPrevLoading] = useState<boolean>(!!context.isLoading);

  // Fresh backend data: restart the countdown and release the in-flight lock.
  if (prevCountdownKey !== countdownKey) {
    setPrevCountdownKey(countdownKey);
    startCountdown();
    releaseSend();
  }

  // Request settled (success or rejection) without new countdown data.
  if (prevLoading !== !!context.isLoading) {
    setPrevLoading(!!context.isLoading);
    if (!context.isLoading) {
      releaseSend();
    }
  }

  const remaining = Math.max(0, Math.ceil((deadline - now) / 1000));
  const timeLeft = remaining > 0;

  function releaseSend() {
    setSubmitting(false);
  }

  function startCountdown() {
    const from = Date.now();
    setNow(from);
    setDeadline(from + expiresIn * 1000);
  }

  // Ref writes must happen outside render, so the in-flight guard is cleared
  // here (after `submitting` goes false) instead of inside releaseSend().
  useEffect(() => {
    if (!submitting) {
      sendingRef.current = false;
    }
  }, [submitting]);

  useEffect(() => {
    if (!timeLeft) {
      return undefined;
    }

    const interval: ReturnType<typeof setInterval> = setInterval(
      () => setNow(Date.now()),
      1000,
    );

    return () => clearInterval(interval);
  }, [timeLeft, deadline]);

  const formatTime = (seconds: number): string => {
    if (seconds <= 0) {
      return t("app.otp.timed_out");
    }
    const m: number = Math.floor(seconds / 60);
    const s: number = seconds % 60;
    return `${m}:${s.toString().padStart(2, "0")}`;
  };

  const submitResend = (captchaToken: string) => {
    if (!context.onSubmit || sendingRef.current || noAttemptsLeft) {
      return;
    }
    sendingRef.current = true;

    // Clear only the OTP field ("otp" ref in flow-esignet.yaml). A full
    // resetForm() would also wipe username and break verification after resend.
    context.onInputChange("otp", "");

    setSubmitting(true);
    context.onSubmit(
      component,
      component.captcha ? { captcha_token: captchaToken } : {},
      true,
    );

    // Countdown starts once the backend confirms (countdownKey change).
    // On failure, releaseSend() re-enables Resend OTP instead.
    setShowCaptcha(false);
    setCaptchaError(false);
  };

  // Reveal the captcha first; sending happens once it is solved. Flows without
  // a captcha configured resend straight away.
  const handleResendClick = () => {
    sendingRef.current = false;

    if (!component.captcha) {
      submitResend("");
      return;
    }
    setCaptchaError(false);
    setShowCaptcha(true);
  };

  const handleCancel = () => {
    setShowCaptcha(false);
    setCaptchaError(false);
    context.onInputChange(captchaId, "");
  };

  const captchaChanged = (token: string | null | undefined) => {
    const solved = typeof token === "string" && token.trim().length > 0;
    setCaptchaError(!solved);

    if (solved) {
      submitResend(token.trim());
    }
  };

  return (
    <div className="resend-otp-panel">
      {timeLeft && (
        <p className="resend-otp-panel__text">
          {t("app.otp.resend_timer")} {formatTime(remaining)}
        </p>
      )}

      {!timeLeft && !showCaptcha && noAttemptsLeft && (
        // Display a message that user has exhausted the resend attempts.
        <p className="resend-otp-panel__text resend-otp-panel__text--error">
          {t("otp.resend_limit_reached")}
        </p>
      )}
      
      {!timeLeft && !showCaptcha && !noAttemptsLeft && (
        <>
          <p className="resend-otp-panel__text">
            {t("otp.didnt_receive_otp")}
          </p>
          <button
            type="button"
            id={component.id}
            className="resend-otp-panel__link"
            onClick={handleResendClick}
            disabled={noAttemptsLeft || submitting}
            data-testid="thunderid-resend-otp-button"
          >
            {t(component?.label ?? "otp.resend_otp")}
          </button>
        </>
      )}

      {!timeLeft && showCaptcha && (
        <>
          <p className="resend-otp-panel__title">{t("otp.resend_otp")}</p>
          <p className="resend-otp-panel__text">
            {t("otp.resend_captcha_prompt")}
          </p>

          <div className="resend-otp-panel__captcha">
            <CaptchaComponent
              component={{ ...component, ref: captchaId }}
              context={context}
              key={captchaId}
              captchaChanged={captchaChanged}
            ></CaptchaComponent>
          </div>

          {captchaError && (
            <p className="resend-otp-panel__error">
              {t("validations.required.field.error")}
            </p>
          )}

          <button
            type="button"
            className="resend-otp-panel__link"
            onClick={handleCancel}
            data-testid="thunderid-resend-otp-cancel"
          >
            {t("button.cancel")}
          </button>
        </>
      )}
    </div>
  );
}
