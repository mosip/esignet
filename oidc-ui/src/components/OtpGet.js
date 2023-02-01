import { useRef, useState } from "react";
import LoadingIndicator from "../common/LoadingIndicator";
import FormAction from "./FormAction";
import { LoadingStates as states } from "../constants/states";
import { useTranslation } from "react-i18next";
import ErrorIndicator from "../common/ErrorIndicator";
import InputWithImage from "./InputWithImage";
import { buttonTypes, configurationKeys } from "../constants/clientConstants";
import ReCAPTCHA from "react-google-recaptcha";

export default function OtpGet({
  param,
  authService,
  openIDConnectService,
  onOtpSent,
  i18nKeyPrefix = "otp",
}) {
  const { t } = useTranslation("translation", { keyPrefix: i18nKeyPrefix });
  const fields = param;
  let fieldsState = {};
  fields.forEach((field) => (fieldsState["Otp" + field.id] = ""));

  const post_SendOtp = authService.post_SendOtp;

  const commaSeparatedChannels =
    openIDConnectService.getIdpConfiguration(configurationKeys.sendOtpChannels) ??
    process.env.REACT_APP_SEND_OTP_CHANNELS;

  const captchaEnableComponents =
    openIDConnectService.getIdpConfiguration(configurationKeys.captchaEnableComponents) ??
    process.env.REACT_APP_CAPTCHA_ENABLE;

  const captchaEnableComponentsList = captchaEnableComponents.split(",").map((x) => x.trim().toLowerCase());
  const showCaptcha = captchaEnableComponentsList.indexOf("otp") !== -1;

  const captchaSiteKey =
    openIDConnectService.getIdpConfiguration(configurationKeys.captchaSiteKey) ??
    process.env.REACT_APP_CAPTCHA_SITE_KEY;

  const [loginState, setLoginState] = useState(fieldsState);
  const [error, setError] = useState(null);
  const [status, setStatus] = useState({ state: states.LOADED, msg: "" });

  const [captchaToken, setCaptchaToken] = useState(null);
  const _reCaptchaRef = useRef(null);

  const handleCaptchaChange = (value) => {
    setCaptchaToken(value);
  };

  const handleChange = (e) => {
    setLoginState({ ...loginState, [e.target.id]: e.target.value });
  };

  const sendOTP = async () => {
    try {
      setError(null);

      let transactionId = openIDConnectService.getTransactionId();
      let vid = loginState["Otp_mosip-vid"];

      let otpChannels = commaSeparatedChannels.split(",").map((x) => x.trim());

      setStatus({ state: states.LOADING, msg: "sending_otp_msg" });
      const sendOtpResponse = await post_SendOtp(
        transactionId,
        vid,
        otpChannels,
        captchaToken
      );
      setStatus({ state: states.LOADED, msg: "" });

      const { response, errors } = sendOtpResponse;

      if (errors != null && errors.length > 0) {
        setError({
          prefix: "send_otp_failed_msg",
          errorCode: errors[0].errorCode,
          defaultMsg: errors[0].errorMessage,
        });
        return;
      } else {
        onOtpSent(vid, response);
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

  return (
    <>
      <div className="mt-12">
        {fields.map((field) => (
          <InputWithImage
            key={"Otp_" + field.id}
            handleChange={handleChange}
            value={loginState["Otp_" + field.id]}
            labelText={t(field.labelText)}
            labelFor={field.labelFor}
            id={"Otp_" + field.id}
            name={field.name}
            type={field.type}
            isRequired={field.isRequired}
            placeholder={t(field.placeholder)}
            imgPath="images/photo_scan.png"
            tooltipMsg="vid_tooltip"
          />
        ))}

        {showCaptcha && (
          <div className="flex justify-center mt-5 mb-5">
            <ReCAPTCHA
              ref={_reCaptchaRef}
              onChange={handleCaptchaChange}
              sitekey={captchaSiteKey}
            />
          </div>
        )}

        <div className="mt-5 mb-5">
          <FormAction
            type={buttonTypes.button}
            text={t("get_otp")}
            handleClick={sendOTP}
            disabled={!loginState["Otp_mosip-vid"]?.trim() || (showCaptcha && captchaToken === null)}
          />
        </div>

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
    </>
  );
}
