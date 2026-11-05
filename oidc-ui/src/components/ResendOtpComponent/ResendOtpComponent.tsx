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
  const [attemptsLeft, setAttemptsLeft] = useState<number>(() =>
    Number(additionalData.attemptsLeft ?? 1),
  );
  const reportedAttempts =
    additionalData.attemptsLeft == null
      ? undefined
      : Number(additionalData.attemptsLeft);
  if (
    reportedAttempts !== undefined &&
    Number.isFinite(reportedAttempts) &&
    reportedAttempts !== attemptsLeft
  ) {
    setAttemptsLeft(reportedAttempts);
  }
  const noAttemptsLeft = Number.isFinite(attemptsLeft) && attemptsLeft <= 0;
  const [totalAttempts, setTotalAttempts] = useState<number>(0);
  const reportedTotal = Number(additionalData.totalAttempts);
  if (
    Number.isInteger(reportedTotal) &&
    reportedTotal > 0 &&
    reportedTotal !== totalAttempts
  ) {
    setTotalAttempts(reportedTotal);
  }
  const showAttempts =
    totalAttempts > 0 &&
    Number.isInteger(attemptsLeft) &&
    attemptsLeft >= 0 &&
    attemptsLeft <= totalAttempts;

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
  const countdownKey =
    additionalData.timeLeft == null
      ? null
      : `${expiresIn}|${additionalData.attemptsLeft ?? ""}`;
  const [prevCountdownKey, setPrevCountdownKey] = useState<string | null>(
    countdownKey,
  );
  const [prevLoading, setPrevLoading] = useState<boolean>(!!context.isLoading);

  // Fresh backend data: restart the countdown and release the in-flight lock.
  if (prevCountdownKey !== countdownKey) {
    setPrevCountdownKey(countdownKey);
    // Verification responses can omit OTP data; keep the existing deadline.
    if (countdownKey !== null) {
      startCountdown();
      releaseSend();
    }
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

  const submitOtpAction = (captchaToken?: string) => {
    if (
      !context.onSubmit ||
      sendingRef.current ||
      context.isLoading ||
      timeLeft
    ) {
      return;
    }
    sendingRef.current = true;

    // Clear only the OTP field ("otp" ref in flow-esignet.yaml). A full
    // resetForm() would also wipe username and break verification after resend.
    context.onInputChange("otp", "");

    setSubmitting(true);
    // Failed verification clears backend inputs, so resend the retained username.
    context.onSubmit(
      component,
      {
        ...(context.formValues.username
          ? { username: context.formValues.username }
          : {}),
        ...(component.captcha && captchaToken !== undefined
          ? { captcha_token: captchaToken }
          : {}),
      },
      true,
    );

    // Countdown starts once the backend confirms (countdownKey change).
    // On failure, releaseSend() re-enables Resend OTP instead.
    setShowCaptcha(false);
    setCaptchaError(false);
  };

  const submitResend = (captchaToken: string) => {
    if (!noAttemptsLeft) {
      submitOtpAction(captchaToken);
    }
  };

  const handleTryAgain = () => {
    // Submit the exhausted OTP action so the backend ends the flow and redirects.
    if (noAttemptsLeft) {
      submitOtpAction();
    }
  };

  // Reveal the captcha first; sending happens once it is solved. Flows without
  // a captcha configured resend straight away.
  const handleResendClick = () => {
    if (timeLeft || submitting || noAttemptsLeft || context.isLoading) {
      return;
    }
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
    <>
      <div className="resend-otp-panel">
        {timeLeft && (
          <p className="resend-otp-panel__text">
            {t("app.otp.resend_timer")} {formatTime(remaining)}
          </p>
        )}

        {!timeLeft && noAttemptsLeft && (
          <p className="resend-otp-panel__text resend-otp-panel__text--error">
            {t("otp.resend_limit_reached")}{" "}
            <button
              type="button"
              className="resend-otp-panel__link resend-otp-panel__retry"
              onClick={handleTryAgain}
              disabled={submitting || !!context.isLoading || !context.onSubmit}
              data-testid="thunderid-resend-otp-retry"
            >
              {t("otp.resend_try_again")}
            </button>
          </p>
        )}

        {!showCaptcha && (timeLeft || !noAttemptsLeft) && (
          <>
            {!timeLeft && (
              <p className="resend-otp-panel__text">
                {t("otp.didnt_receive_otp")}
              </p>
            )}
            <button
              type="button"
              id={component.id}
              className="resend-otp-panel__link"
              onClick={handleResendClick}
              disabled={
                timeLeft || noAttemptsLeft || submitting || !!context.isLoading
              }
              data-testid="thunderid-resend-otp-button"
            >
              {t(component?.label ?? "otp.resend_otp")}
            </button>
          </>
        )}

        {showAttempts && (timeLeft || !noAttemptsLeft) && (
          <div className="resend-otp-attempts" role="status">
            {t("otp.attempts_remaining", { attemptsLeft, totalAttempts })}
          </div>
        )}

        {!timeLeft && showCaptcha && !noAttemptsLeft && (
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
    </>
  );
}
