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
import ErrorIndicator from "../common/ErrorIndicator";
import InputWithImage from "./InputWithImage";
import PinInput from "react-pin-input";

export default function OtpVerify({
  param,
  otpResponse,
  vid,
  authService,
  openIDConnectService,
  i18nKeyPrefix = "otp",
}) {
  const { t } = useTranslation("translation", { keyPrefix: i18nKeyPrefix });
  const fields = param;
  let fieldsState = {};
  fields.forEach((field) => (fieldsState["Otp" + field.id] = ""));

  const post_SendOtp = authService.post_SendOtp;
  const post_AuthenticateUser = authService.post_AuthenticateUser;

  const resendOtpTimeout =
    openIDConnectService.getIdpConfiguration(configurationKeys.resendOtpTimeout) ??
    process.env.REACT_APP_RESEND_OTP_TIMEOUT_IN_SEC;
  const commaSeparatedChannels =
    openIDConnectService.getIdpConfiguration(configurationKeys.sendOtpChannels) ??
    process.env.REACT_APP_SEND_OTP_CHANNELS;

  const [loginState, setLoginState] = useState(fieldsState);
  const [error, setError] = useState(null);
  const [status, setStatus] = useState({ state: states.LOADED, msg: "" });
  const [resendOtpCountDown, setResendOtpCountDown] = useState();
  const [showResendOtp, setShowResendOtp] = useState(false);
  const [showTimer, setShowTimer] = useState(false);
  const [timer, setTimer] = useState(null);
  const [otpValue, setOtpValue] = useState("");
  const [otpSentEmail, setOtpSentEmail] = useState("");
  const [otpSentMobile, setOtpSentMobile] = useState("");
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
      setError(null);
      pin.clear();
      setOtpValue("");

      let transactionId = openIDConnectService.getTransactionId();
      let otpChannels = commaSeparatedChannels.split(",").map((x) => x.trim());

      setStatus({ state: states.LOADING, msg: "sending_otp_msg" });
      const sendOtpResponse = await post_SendOtp(
        transactionId,
        vid,
        otpChannels
      );
      setStatus({ state: states.LOADED, msg: "" });

      const { response, errors } = sendOtpResponse;

      if (errors != null && errors.length > 0) {
        setError({
          errorCode: errors[0].errorCode,
          defaultMsg: errors[0].errorMessage,
        });
        return;
      } else {
        startTimer();

        setOtpSentMobile(response.maskedMobile);
        setOtpSentEmail(response.maskedEmail);
      }
    } catch (error) {
      setError({
        prefix: "send_otp_failed_msg",
        errorCode: error.message,
        defaultMsg: error.message,
      });
      setStatus({ state: states.ERROR, msg: "" });
    }
  };

  useEffect(() => {
    setShowTimer(false);
    setShowResendOtp(false);
    setError(null);

    setOtpSentMobile(otpResponse.maskedMobile);
    setOtpSentEmail(otpResponse.maskedEmail);

    startTimer();
  }, []);

  const startTimer = async () => {
    clearInterval(timer);
    setResendOtpCountDown(
      t("resend_otp_counter", getMinFromSec(resendOtpTimeout))
    );
    setShowResendOtp(false);
    setShowTimer(true);
    let timePassed = 0;
    var interval = setInterval(function () {
      timePassed++;
      let timeLeft = resendOtpTimeout - timePassed;
      setResendOtpCountDown(t("resend_otp_counter", getMinFromSec(timeLeft)));

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

      setStatus({ state: states.LOADING, msg: "authenticating_msg" });
      const authenticateResponse = await post_AuthenticateUser(
        transactionId,
        vid,
        challengeList
      );
      setStatus({ state: states.LOADED, msg: "" });

      const { errors } = authenticateResponse;

      if (errors != null && errors.length > 0) {
        setError({
          errorCode: errors[0].errorCode,
          defaultMsg: errors[0].errorMessage,
        });
        return;
      } else {
        setError(null);

        let nonce = openIDConnectService.getNonce();
        let state = openIDConnectService.getState();

        let params = "?";
        if (nonce) {
          params = params + "nonce=" + nonce + "&";
        }
        if (state) {
          params = params + "state=" + state + "&";
        }

        let responseB64 = openIDConnectService.encodeBase64(openIDConnectService.getOAuthDetails());

        //REQUIRED
        params = params + "response=" + responseB64;

        navigate("/consent" + params, {
          replace: true,
        });
      }
    } catch (error) {
      setError({
        prefix: "authentication_failed_msg",
        errorCode: error.message,
        defaultMsg: error.message,
      });
      setStatus({ state: states.ERROR, msg: "" });
    }
  };

  return (
    <>
      <form className="mt-2 space-y-2" onSubmit={handleSubmit}>
        <div className={"space-y-px"}>
          {fields.map((field) => (
            <InputWithImage
              key={"Otp_" + field.id}
              handleChange={handleChange}
              value={vid}
              labelText={t(field.labelText)}
              labelFor={field.labelFor}
              id={"Otp_" + field.id}
              name={field.name}
              type={field.type}
              isRequired={field.isRequired}
              placeholder={t(field.placeholder)}
              imgPath="images/photo_scan.png"
              disabled={true}
              customClass="text-gray-400"
              tooltipMsg="vid_tooltip"
            />
          ))}
        </div>

        <div className="space-y-px flex justify-center">
          <PinInput
            length={6}
            initialValue=""
            onChange={(value, index) => {
              setOtpValue(value);
            }}
            type="numeric"
            inputMode="number"
            style={{ padding: "5px 0px" }}
            inputStyle={{
              width: "40px",
              height: "40px",
              margin: "0 5px",
              border: "",
              borderBottom: "2px solid #0284c7",
              color: "#0284c7",
            }}
            inputFocusStyle={{ borderBottom: "2px solid #075985" }}
            onComplete={(value, index) => {
              //TO handle case when user pastes OTP
              setOtpValue(value);
            }}
            autoSelect={true}
            ref={(n) => (pin = n)}
          />
        </div>

        <div className="h-16 flex items-center justify-center">
          {status.state !== states.LOADING && !error && (
            <span className="w-full flex justify-center text-sm text-gray-500">
              {otpSentEmail && otpSentMobile
                ? t("otp_sent_msg", {
                  otpChannels: t("mobile_email_placeholder", {
                    mobileNumber: otpSentMobile,
                    emailAddress: otpSentEmail,
                  }),
                })
                : otpSentEmail
                  ? t("otp_sent_msg", {
                    otpChannels: t("email_placeholder", {
                      emailAddress: otpSentEmail,
                    }),
                  })
                  : t("otp_sent_msg", {
                    otpChannels: t("mobile_placeholder", {
                      mobileNumber: otpSentMobile,
                    }),
                  })}
            </span>
          )}

          {status.state === states.LOADING && (
            <LoadingIndicator size="medium" message={status.msg} />
          )}

          {status.state !== states.LOADING && error && (
            <ErrorIndicator
              prefix={error.prefix}
              errorCode={error.errorCode}
              defaultMsg={error.defaultMsg}
            />
          )}
        </div>

        <FormAction
          disabled={otpValue.length !== 6}
          type={buttonTypes.submit}
          text={t("verify")}
        />
        {showTimer && (
          <span className="w-full flex justify-center text-sm text-gray-500">
            {resendOtpCountDown}
          </span>
        )}
        {showResendOtp && (
          <FormAction
            type={buttonTypes.button}
            text={t("resend_otp")}
            handleClick={handleSendOtp}
          />
        )}
      </form>
    </>
  );
}
