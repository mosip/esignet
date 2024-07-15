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
import InputWithImage from "./InputWithImage";
import PinInput from "react-pin-input";
import ErrorBanner from "../common/ErrorBanner";
import langConfigService from "../services/langConfigService";
import redirectOnError from "../helpers/redirectOnError";

const langConfig = await langConfigService.getEnLocaleConfiguration();

export default function OtpVerify({
  param,
  otpResponse,
  vid,
  authService,
  openIDConnectService,
  i18nKeyPrefix1 = "otp",
  i18nKeyPrefix2 = "errors",
  captcha
}) {

  const { t: t1 } = useTranslation("translation", { keyPrefix: i18nKeyPrefix1 });
  const { t: t2 } = useTranslation("translation", { keyPrefix: i18nKeyPrefix2 });

  const inputCustomClass =
    "h-10 border border-input bg-transparent px-3 py-2 text-sm ring-offset-background file:border-0 file:bg-transparent file:text-sm file:font-medium placeholder:text-[hsla(0, 0%, 51%)] focus-visible:outline-none disabled:cursor-not-allowed disabled:bg-muted-light-gray shadow-none text-gray-400";

  const fields = param;
  let fieldsState = {};
  fields.forEach((field) => (fieldsState["Otp" + field.id] = ""));

  const post_SendOtp = authService.post_SendOtp;
  const post_AuthenticateUser = authService.post_AuthenticateUser;
  const buildRedirectParams = authService.buildRedirectParams;

  const resendOtpTimeout =
    openIDConnectService.getEsignetConfiguration(configurationKeys.resendOtpTimeout) ??
    process.env.REACT_APP_RESEND_OTP_TIMEOUT_IN_SEC;
  const commaSeparatedChannels =
    openIDConnectService.getEsignetConfiguration(configurationKeys.sendOtpChannels) ??
    process.env.REACT_APP_SEND_OTP_CHANNELS;
  const otpLengthValue =
    openIDConnectService.getEsignetConfiguration(configurationKeys.otpLength) ??
    process.env.REACT_APP_OTP_LENGTH;
  const otpLength = parseInt(otpLengthValue);

  const [loginState, setLoginState] = useState(fieldsState);
  const [status, setStatus] = useState({ state: states.LOADED, msg: "" });
  const [resendOtpCountDown, setResendOtpCountDown] = useState();
  const [showResendOtp, setShowResendOtp] = useState(false);
  const [showTimer, setShowTimer] = useState(false);
  const [timer, setTimer] = useState(null);
  const [otpValue, setOtpValue] = useState("");
  const [otpSentEmail, setOtpSentEmail] = useState("");
  const [otpSentMobile, setOtpSentMobile] = useState("");
  const [errorBanner, setErrorBanner] = useState(null);

  let pin = useRef();

  const navigate = useNavigate();

  const handleChange = (e) => {
    setLoginState({ ...loginState, [e.target.id]: e.target.value });
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    authenticateUser();
  };

  const handleSendOtp = (e) => {
    e.preventDefault();
    sendOTP();
  };

  const sendOTP = async () => {
    try {
      setErrorBanner(null);
      pin.clear();
      setOtpValue("");

      let transactionId = openIDConnectService.getTransactionId();
      let otpChannels = commaSeparatedChannels.split(",").map((x) => x.trim());
      
      let idvid = fields[0].prefix + vid + fields[0].postfix;

      setStatus({ state: states.LOADING, msg: "sending_otp_msg" });
      const sendOtpResponse = await post_SendOtp(
        transactionId,
        idvid,
        otpChannels
      );
      setStatus({ state: states.LOADED, msg: "" });

      const { response, errors } = sendOtpResponse;

      if (errors != null && errors.length > 0) {

        let errorCodeCondition = langConfig.errors.otp[errors[0].errorCode] !== undefined && langConfig.errors.otp[errors[0].errorCode] !== null;

        if (errorCodeCondition) {
          setErrorBanner({
            errorCode: `otp.${errors[0].errorCode}`,
            show: true
          });
        }
        else if (errors[0].errorCode === "invalid_transaction") {
          redirectOnError(errors[0].errorCode, t2(`${errors[0].errorCode}`));
        }
        else {
          setErrorBanner({
            errorCode: `${errors[0].errorCode}`,
            show: true
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
        show: true
      });
      setStatus({ state: states.ERROR, msg: "" });
    }
  };

  useEffect(() => {
    setShowTimer(false);
    setShowResendOtp(false);
    setErrorBanner(null);
    setOtpSentMobile(otpResponse.maskedMobile);
    setOtpSentEmail(otpResponse.maskedEmail);

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
          format: challengeFormat
        },
      ];

      let idvid = fields[0].prefix + vid + fields[0].postfix;

      setStatus({ state: states.LOADING, msg: "authenticating_msg" });
      const authenticateResponse = await post_AuthenticateUser(
        transactionId,
        idvid,
        challengeList,
        captcha
      );
      setStatus({ state: states.LOADED, msg: "" });

      const { response, errors } = authenticateResponse;

      if (errors != null && errors.length > 0) {

        let errorCodeCondition = langConfig.errors.otp[errors[0].errorCode] !== undefined && langConfig.errors.otp[errors[0].errorCode] !== null;

        if (errorCodeCondition) {
          setErrorBanner({
            errorCode: `otp.${errors[0].errorCode}`,
            show: true
          });
        }
        else if (errors[0].errorCode === "invalid_transaction") {
          redirectOnError(errors[0].errorCode, t2(`${errors[0].errorCode}`));
        }
        else {
          setErrorBanner({
            errorCode: `${errors[0].errorCode}`,
            show: true
          });
        }
        return;
      } else {
        setErrorBanner(null);
        let nonce = openIDConnectService.getNonce();
        let state = openIDConnectService.getState();

        let params = buildRedirectParams(nonce, state, openIDConnectService.getOAuthDetails(), response.consentAction);

        navigate(process.env.PUBLIC_URL + "/claim-details" + params, {
          replace: true,
        });
      }
    } catch (error) {
      setErrorBanner({
        errorCode: "authentication_failed_msg",
        show: true
      });
      setStatus({ state: states.ERROR, msg: "" });
    }
  };

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

      <form className="mt-6 space-y-2" onSubmit={handleSubmit}>
        <div className={"space-y-px"}>
          {fields.map((field) => (
            <InputWithImage
              key={"Otp_" + field.id}
              handleChange={handleChange}
              value={vid}
              labelText={t1(field.labelText)}
              labelFor={field.labelFor}
              id={"Otp_" + field.id}
              name={field.name}
              type={field.type}
              isRequired={field.isRequired}
              placeholder={t1(field.placeholder)}
              customClass={inputCustomClass}
              imgPath="images/photo_scan.png"
              disabled={true}
              tooltipMsg="vid_info"
              prefix={field.prefix}
              maxLength={field.maxLength}
              regex={field.regex}
            />
          ))}
        </div>

        <div className="space-y-px flex justify-center" id="otp_verify_input">
          <PinInput
            length={otpLength}
            initialValue=""
            onChange={(value, index) => {
              setOtpValue(value);
            }}
            type="numeric"
            inputMode="number"
            onComplete={(value, index) => {
              //TO handle case when user pastes OTP
              setOtpValue(value);
            }}
            autoSelect={true}
            ref={(n) => (pin = n)}
          />
        </div>

        <div className="text-center break-all">
          {status.state !== states.LOADING && !errorBanner && (
            <span className="w-full flex justify-center text-sm text-gray-500">
              {otpSentEmail && otpSentMobile
                ? t1("otp_sent_msg", {
                  otpChannels: t1("mobile_email_placeholder", {
                    mobileNumber: otpSentMobile,
                    emailAddress: otpSentEmail,
                  }),
                })
                : otpSentEmail
                  ? t1("otp_sent_msg", {
                    otpChannels: t1("email_placeholder", {
                      emailAddress: otpSentEmail,
                    }),
                  })
                  : t1("otp_sent_msg", {
                    otpChannels: t1("mobile_placeholder", {
                      mobileNumber: otpSentMobile,
                    }),
                  })}
            </span>
          )}

          {status.state === states.LOADING && (
            <LoadingIndicator size="medium" message={status.msg} />
          )}
        </div>

        <FormAction
          disabled={otpValue.length !== otpLength}
          type={buttonTypes.submit}
          text={t1("verify")}
          id="verify_otp"
        />
        {showTimer && (
          <span className="w-full flex justify-center text-sm text-gray-500">
            {resendOtpCountDown}
          </span>
        )}
        {showResendOtp && (
          <FormAction
            type={buttonTypes.button}
            text={t1("resend_otp")}
            handleClick={handleSendOtp}
            id="resend_otp"
          />
        )}
      </form>
    </>
  );
}
