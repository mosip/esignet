import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import LoadingIndicator from "../common/LoadingIndicator";
import FormAction from "./FormAction";
import { LoadingStates as states } from "../constants/states";
import {
  buttonTypes,
  challengeFormats,
  challengeTypes,
  configurationKeys,
} from "../constants/clientConstants";
import { useTranslation } from "react-i18next";
import PinInput from "react-pin-input";
import ErrorBanner from "../common/ErrorBanner";
import langConfigService from "../services/langConfigService";
import redirectOnError from "../helpers/redirectOnError";
import ReCAPTCHA from "react-google-recaptcha";

export default function OtpVerify({
  param,
  otpResponse,
  ID,
  authService,
  openIDConnectService,
  i18nKeyPrefix1 = "otp",
  i18nKeyPrefix2 = "errors",
}) {
  const { t: t1, i18n } = useTranslation("translation", {
    keyPrefix: i18nKeyPrefix1,
  });
  const { t: t2 } = useTranslation("translation", {
    keyPrefix: i18nKeyPrefix2,
  });

  const [langConfig, setLangConfig] = useState(null);

  useEffect(() => {
    async function loadLangConfig() {
      try {
        const config = await langConfigService.getEnLocaleConfiguration();
        setLangConfig(config);
      } catch (e) {
        console.error("Failed to load lang config", e);
        setLangConfig({ errors: { otp: {} } }); // Fallback to prevent crashes
      }
    }

    loadLangConfig();
  }, []);

  const fields = param;
  let fieldsState = {};
  fields.forEach((field) => (fieldsState["Otp" + field.id] = ""));

  const post_SendOtp = authService.post_SendOtp;
  const post_AuthenticateUser = authService.post_AuthenticateUser;
  const buildRedirectParams = authService.buildRedirectParams;

  const resendOtpTimeout =
    openIDConnectService.getEsignetConfiguration(
      configurationKeys.resendOtpTimeout
    ) ?? process.env.REACT_APP_RESEND_OTP_TIMEOUT_IN_SEC;
  const commaSeparatedChannels =
    openIDConnectService.getEsignetConfiguration(
      configurationKeys.sendOtpChannels
    ) ?? process.env.REACT_APP_SEND_OTP_CHANNELS;
  const otpLengthValue =
    openIDConnectService.getEsignetConfiguration(configurationKeys.otpLength) ??
    process.env.REACT_APP_OTP_LENGTH;
  const otpLength = parseInt(otpLengthValue);

  const [status, setStatus] = useState({ state: states.LOADED, msg: "" });
  const [resendOtpCountDown, setResendOtpCountDown] = useState();
  const [showResendOtp, setShowResendOtp] = useState(false);
  const [showTimer, setShowTimer] = useState(false);
  const [timer, setTimer] = useState(null);
  const [otpValue, setOtpValue] = useState("");
  const [otpSentEmail, setOtpSentEmail] = useState("");
  const [otpSentMobile, setOtpSentMobile] = useState("");
  const [errorBanner, setErrorBanner] = useState(null);

  const captchaSiteKey =
    openIDConnectService.getEsignetConfiguration(
      configurationKeys.captchaSiteKey
    ) ?? process.env.REACT_APP_CAPTCHA_SITE_KEY;

  const captchaEnableComponents =
    openIDConnectService.getEsignetConfiguration(
      configurationKeys.captchaEnableComponents
    ) ?? process.env.REACT_APP_CAPTCHA_ENABLE;

  const captchaEnableComponentsList = captchaEnableComponents
    .split(",")
    .map((x) => x.trim().toLowerCase());

  const [showCaptcha, setShowCaptcha] = useState(
    captchaEnableComponentsList.indexOf("send-otp") !== -1
  );
  const [captchaToken, setCaptchaToken] = useState(null);
  const _reCaptchaRef = useRef(null);

  let pin = useRef();

  const navigate = useNavigate();

  const handleSubmit = (e) => {
    e.preventDefault();
    authenticateUser();
  };

  const handleSendOtp = (e) => {
    e.preventDefault();
    sendOTP();
  };

  /**
   * Reset the captcha widget
   * & its token value
   */
  const resetCaptcha = () => {
    _reCaptchaRef.current?.reset();
    setCaptchaToken(null);
  };

  const handleCaptchaChange = (value) => {
    setCaptchaToken(value);
  };

  const sendOTP = async () => {
    try {
      setErrorBanner(null);
      pin.clear();
      setOtpValue("");

      let transactionId = openIDConnectService.getTransactionId();
      let otpChannels = commaSeparatedChannels.split(",").map((x) => x.trim());

      let id = ID.prefix + ID.id + ID.postfix;
      let tempCaptchaToken = captchaToken;
      setCaptchaToken(null);

      setStatus({ state: states.LOADING, msg: "sending_otp_msg" });
      const sendOtpResponse = await post_SendOtp(
        transactionId,
        id,
        otpChannels,
        tempCaptchaToken
      );
      setStatus({ state: states.LOADED, msg: "" });

      const { response, errors } = sendOtpResponse;

      if (errors != null && errors.length > 0) {
        let errorCodeCondition =
          langConfig.errors.otp[errors[0].errorCode] !== undefined &&
          langConfig.errors.otp[errors[0].errorCode] !== null;

        if (errorCodeCondition) {
          setErrorBanner({
            errorCode: `otp.${errors[0].errorCode}`,
            show: true,
          });
        } else if (errors[0].errorCode === "invalid_transaction") {
          redirectOnError(errors[0].errorCode, t2(`${errors[0].errorCode}`));
        } else {
          setErrorBanner({
            errorCode: `${errors[0].errorCode}`,
            show: true,
          });
        }
        return;
      } else {
        startTimer();
        setErrorBanner(null);
        setOtpSentMobile(response.maskedMobile);
        setOtpSentEmail(response.maskedEmail);
      }
    } catch (error) {
      setErrorBanner({
        errorCode: "otp.send_otp_failed_msg",
        show: true,
      });
      setStatus({ state: states.ERROR, msg: "" });
    }
  };

  useEffect(() => {
    let loadComponent = async () => {
      i18n.on("languageChanged", function (lng) {
        if (showCaptcha) {
          //to rerender recaptcha widget on language change
          setShowCaptcha(false);
          setTimeout(() => {
            setShowCaptcha(true);
          }, 1);
        }
      });
    };

    setShowTimer(false);
    setShowResendOtp(false);
    setErrorBanner(null);
    setOtpSentMobile(otpResponse.maskedMobile);
    setOtpSentEmail(otpResponse.maskedEmail);
    loadComponent();

    startTimer();
  }, []);

  const startTimer = async () => {
    clearInterval(timer);
    setResendOtpCountDown(
      t1("resend_otp_counter", getMinFromSec(resendOtpTimeout))
    );
    setShowResendOtp(false);
    setShowTimer(true);
    let timePassed = 0;
    var interval = setInterval(function () {
      timePassed++;
      let timeLeft = resendOtpTimeout - timePassed;
      setResendOtpCountDown(t1("resend_otp_counter", getMinFromSec(timeLeft)));

      if (timeLeft === 0) {
        clearInterval(interval);
        setShowTimer(false);
        setShowResendOtp(true);
        resetCaptcha();
      }
    }, 1000);
    setTimer(interval);
  };

  const getMinFromSec = (seconds) => {
    let sec = (seconds % 60).toLocaleString("en-US", {
      minimumIntegerDigits: 2,
      useGrouping: false,
    });

    let min = Math.floor(seconds / 60).toLocaleString("en-US", {
      minimumIntegerDigits: 2,
      useGrouping: false,
    });

    return { min: min, sec: sec };
  };

  //Handle Login API Integration here
  const authenticateUser = async () => {
    try {
      let transactionId = openIDConnectService.getTransactionId();

      let challengeType = challengeTypes.otp;
      let challenge = otpValue;
      let challengeFormat = challengeFormats.otp;

      let challengeList = [
        {
          authFactorType: challengeType,
          challenge: challenge,
          format: challengeFormat,
        },
      ];

      let id = ID.prefix + ID.id + ID.postfix;

      setStatus({ state: states.LOADING, msg: "authenticating_msg" });
      const authenticateResponse = await post_AuthenticateUser(
        transactionId,
        id,
        challengeList
      );
      setStatus({ state: states.LOADED, msg: "" });

      const { response, errors } = authenticateResponse;

      if (errors != null && errors.length > 0) {
        let errorCodeCondition =
          langConfig.errors.otp[errors[0].errorCode] !== undefined &&
          langConfig.errors.otp[errors[0].errorCode] !== null;

        if (errorCodeCondition) {
          setErrorBanner({
            errorCode: `otp.${errors[0].errorCode}`,
            show: true,
          });
        } else if (errors[0].errorCode === "invalid_transaction") {
          redirectOnError(errors[0].errorCode, t2(`${errors[0].errorCode}`));
        } else {
          setErrorBanner({
            errorCode: `${errors[0].errorCode}`,
            show: true,
          });
        }
        return;
      } else {
        setErrorBanner(null);
        let nonce = openIDConnectService.getNonce();
        let state = openIDConnectService.getState();

        let params = buildRedirectParams(
          nonce,
          state,
          openIDConnectService.getOAuthDetails(),
          response.consentAction
        );

        navigate(process.env.PUBLIC_URL + "/claim-details" + params, {
          replace: true,
        });
      }
    } catch (error) {
      setErrorBanner({
        errorCode: "authentication_failed_msg",
        show: true,
      });
      setStatus({ state: states.ERROR, msg: "" });
    }
  };

  let styles = {
    width: "40px",
    height: "40px",
    margin: "0 5px",
    border: "",
    borderBottom: "2px solid #0284c7",
    color: "#0284c7",
  };

  if (window.screen.availWidth <= 375) {
    styles = { ...styles, width: "2em" };
  }

  const onCloseHandle = () => {
    setErrorBanner(null);
  };

  return (
    <>
      {errorBanner !== null && (
        <ErrorBanner
          showBanner={errorBanner.show}
          errorCode={t2(errorBanner.errorCode)}
          onCloseHandle={onCloseHandle}
        />
      )}

      <form onSubmit={handleSubmit}>
        <div className="text-center break-words">
          {status.state !== states.LOADING && (
            <div className="w-full m-auto text-gray-500 mt-5 mb-1">
              {otpSentMobile && otpSentEmail ? (
                <>
                  {t1("otp_sent_msg", {
                    otpLength: otpLength,
                  })}
                  <h6 className="text-black">
                    {otpSentMobile}
                    <span className="mx-1">{t1("and")}</span>
                    {otpSentEmail}
                  </h6>
                </>
              ) : otpSentMobile ? (
                <>
                  {t1("otp_sent_msg", {
                    otpLength: otpLength,
                  })}
                  <h6 className="text-black">{otpSentMobile}</h6>
                </>
              ) : (
                <>
                  {t1("otp_sent_msg", {
                    otpLength: otpLength,
                  })}
                  <h6 className="text-black">{otpSentEmail}</h6>
                </>
              )}
            </div>
          )}
        </div>

        <div
          className="space-y-px flex justify-center mb-6"
          id="otp_verify_input"
        >
          <PinInput
            length={otpLength}
            initialValue=""
            onChange={(value, index) => {
              setOtpValue(value);
            }}
            secret
            secretDelay={1}
            type="numeric"
            inputMode="number"
            style={{ padding: "5px 0px" }}
            inputStyle={styles}
            inputFocusStyle={{ borderBottom: "2px solid #075985" }}
            onComplete={(value, index) => {
              //TO handle case when user pastes OTP
              setOtpValue(value);
            }}
            autoSelect={true}
            ref={(n) => (pin = n)}
            disabled={status.state === states.LOADING}
            focus={true}
          />
        </div>

        {showCaptcha && showResendOtp && (
          <div className="flex justify-center mt-5 mb-5">
            <ReCAPTCHA
              hl={i18n.language}
              ref={_reCaptchaRef}
              onChange={handleCaptchaChange}
              sitekey={captchaSiteKey}
            />
          </div>
        )}

        <FormAction
          disabled={
            otpValue.length !== otpLength || status.state === states.LOADING
          }
          type={buttonTypes.submit}
          text={t1("verify")}
          id="verify_otp"
        />

        {showTimer && (
          <span className="w-full flex justify-center mt-6">
            {resendOtpCountDown}
          </span>
        )}

        <div className="my-2">
          <FormAction
            type={buttonTypes.button}
            text={t1("resend_otp")}
            handleClick={handleSendOtp}
            id="resend_otp"
            disabled={
              (showCaptcha && captchaToken === null) ||
              !showResendOtp ||
              status.state === states.LOADING
            }
            customClassName={`!bg-white !border-none !p-0 !w-max !m-auto ${
              showResendOtp ? "resend_otp" : "!text-gray-400"
            }`}
          />
        </div>

        {status.state === states.LOADING && (
          <LoadingIndicator size="medium" message={status.msg} />
        )}
      </form>
    </>
  );
}
