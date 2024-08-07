import { useEffect, useRef, useState } from "react";
import LoadingIndicator from "../common/LoadingIndicator";
import FormAction from "./FormAction";
import { LoadingStates as states } from "../constants/states";
import { useTranslation } from "react-i18next";
import InputWithImage from "./InputWithImage";
import { buttonTypes, configurationKeys } from "../constants/clientConstants";
import ReCAPTCHA from "react-google-recaptcha";
import ErrorBanner from "../common/ErrorBanner";
import langConfigService from "../services/langConfigService";
import redirectOnError from "../helpers/redirectOnError";

const langConfig = await langConfigService.getEnLocaleConfiguration();

export default function OtpGet({
  param,
  authService,
  openIDConnectService,
  onOtpSent,
  i18nKeyPrefix1 = "otp",
  i18nKeyPrefix2 = "errors",
  getCaptchaToken
}) {

  const { t: t1, i18n } = useTranslation("translation", {
    keyPrefix: i18nKeyPrefix1,
  });

  const { t: t2 } = useTranslation("translation", {
    keyPrefix: i18nKeyPrefix2,
  });
  
  const inputCustomClass =
    "h-10 border border-input bg-transparent px-3 py-2 text-sm ring-offset-background file:border-0 file:bg-transparent file:text-sm file:font-medium placeholder:text-[hsla(0, 0%, 51%)] focus-visible:outline-none disabled:cursor-not-allowed disabled:bg-muted-light-gray shadow-none";

  const fields = param;
  let fieldsState = {};
  fields.forEach((field) => (fieldsState["Otp_" + field.id] = ""));

  const post_SendOtp = authService.post_SendOtp;

  const commaSeparatedChannels =
    openIDConnectService.getEsignetConfiguration(configurationKeys.sendOtpChannels) ??
    process.env.REACT_APP_SEND_OTP_CHANNELS;

  const captchaEnableComponents =
    openIDConnectService.getEsignetConfiguration(configurationKeys.captchaEnableComponents) ??
    process.env.REACT_APP_CAPTCHA_ENABLE;

  const captchaEnableComponentsList = captchaEnableComponents
    .split(",")
    .map((x) => x.trim().toLowerCase());

  const [showCaptcha, setShowCaptcha] = useState(
    captchaEnableComponentsList.indexOf("send-otp") !== -1
  );

  const captchaSiteKey =
    openIDConnectService.getEsignetConfiguration(configurationKeys.captchaSiteKey) ??
    process.env.REACT_APP_CAPTCHA_SITE_KEY;

  const [loginState, setLoginState] = useState(fieldsState);
  const [status, setStatus] = useState({ state: states.LOADED, msg: "" });
  const [errorBanner, setErrorBanner] = useState(null);
  const [inputError, setInputError] = useState(null);

  const [captchaToken, setCaptchaToken] = useState(null);
  const _reCaptchaRef = useRef(null);

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

    loadComponent();
  }, []);

  const handleCaptchaChange = (value) => {
    setCaptchaToken(value);
    getCaptchaToken(value);
  };

  const handleChange = (e) => {
    setLoginState({ ...loginState, [e.target.id]: e.target.value });
  };
  
  /**
   * Reset the captcha widget
   * & its token value
   */
  const resetCaptcha = () => {
    _reCaptchaRef.current.reset();
    setCaptchaToken(null);
    getCaptchaToken(null);
  }

  const sendOTP = async () => {
    try {

      let transactionId = openIDConnectService.getTransactionId();
      let vid = fields[0].prefix + loginState["Otp_mosip-vid"] + fields[0].postfix;

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
        if (showCaptcha) {
          resetCaptcha();
        }
        return;
      } else {
        onOtpSent(loginState["Otp_mosip-vid"], response);
        setErrorBanner(null);
      }
    } catch (error) {
      setErrorBanner({
        errorCode: "otp.send_otp_failed_msg",
        show: true
      });
      setStatus({ state: states.ERROR, msg: "" });
      if (showCaptcha) {
        resetCaptcha();
      }
    }
  };

  const onCloseHandle = () => {
    setErrorBanner(null);
  };

  const onBlurChange = (e, errors) => {
    setInputError(errors.length === 0 ? null : errors);
  }

  return (
    <>
      {errorBanner !== null && (
        <ErrorBanner
          showBanner={errorBanner.show}
          errorCode={t2(errorBanner.errorCode)}
          onCloseHandle={onCloseHandle}
        />
      )}

      <div className="mt-6">
        {fields.map((field) => (
          <InputWithImage
            key={"Otp_" + field.id}
            handleChange={handleChange}
            blurChange={onBlurChange}
            value={loginState["Otp_" + field.id]}
            labelText={t1(field.labelText)}
            labelFor={field.labelFor}
            id={"Otp_" + field.id}
            name={field.name}
            type={field.type}
            isRequired={field.isRequired}
            placeholder={t1(field.placeholder)}
            customClass={inputCustomClass}
            imgPath="images/photo_scan.png"
            tooltipMsg="vid_info"
            prefix={field.prefix}
            errorCode={field.errorCode}
            maxLength={field.maxLength}
            regex={field.regex}
            icon={field.infoIcon}
          />
        ))}

        {showCaptcha && (
          <div className="flex justify-center mt-5 mb-5">
            <ReCAPTCHA
              hl={i18n.language}
              ref={_reCaptchaRef}
              onChange={handleCaptchaChange}
              sitekey={captchaSiteKey}
            />
          </div>
        )}

        <div className="mt-5 mb-5">
          <FormAction
            type={buttonTypes.button}
            text={t1("get_otp")}
            handleClick={sendOTP}
            id="get_otp"
            disabled={!loginState["Otp_mosip-vid"]?.trim() || inputError || (showCaptcha && captchaToken === null)}
          />
        </div>

        {status.state === states.LOADING && (
          <LoadingIndicator size="medium" message={status.msg} />
        )}
      </div>
    </>
  );
}
