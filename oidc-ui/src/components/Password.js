import { useEffect, useState, useRef } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import LoadingIndicator from "../common/LoadingIndicator";
import {
  buttonTypes,
  challengeFormats,
  challengeTypes,
  configurationKeys,
} from "../constants/clientConstants";
import { passwordFields } from "../constants/formFields";
import { LoadingStates as states } from "../constants/states";
import FormAction from "./FormAction";
import InputWithImage from "./InputWithImage";
import ReCAPTCHA from "react-google-recaptcha";
import ErrorBanner from "../common/ErrorBanner";
import langConfigService from "../services/langConfigService";
import redirectOnError from "../helpers/redirectOnError";

const fields = passwordFields;
let fieldsState = {};
fields.forEach((field) => (fieldsState["Password_" + field.id] = ""));

const langConfig = await langConfigService.getEnLocaleConfiguration();  

export default function Password({
  param,
  authService,
  openIDConnectService,
  backButtonDiv,
  i18nKeyPrefix1 = "password",
  i18nKeyPrefix2 = "errors",
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
  const post_AuthenticateUser = authService.post_AuthenticateUser;
  const buildRedirectParams = authService.buildRedirectParams;

  const [loginState, setLoginState] = useState(fieldsState);
  const [errorBanner, setErrorBanner] = useState(null);
  const [inputErrorBanner, setInputErrorBanner] = useState([]);
  const [status, setStatus] = useState(states.LOADED);
  const [invalidState, setInvalidState] = useState(true);

  const [forgotPassword, setForgotPassword] = useState(false);
  const [forgotPasswordURL, setForgotPasswordURL] = useState("");

  let forgotPasswordConfig = openIDConnectService.getEsignetConfiguration(
    configurationKeys.forgotPasswordConfig
  );
  
  useEffect(() => {
    if(forgotPasswordConfig?.[configurationKeys.forgotPassword]) {
      setForgotPassword(true);
      setForgotPasswordURL(forgotPasswordConfig[configurationKeys.forgotPasswordURL] + "#" + authService.getAuthorizeQueryParam())
    }
  }, [i18n.language]);

  const bannerCloseTimer =
    openIDConnectService.getEsignetConfiguration(
      configurationKeys.bannerCloseTimer
    ) ?? "";

  const navigate = useNavigate();

  const handleChange = (e) => {
    onCloseHandle();
    setLoginState({ ...loginState, [e.target.id]: e.target.value });
  };
  
  const handleSubmit = (e) => {
    e.preventDefault();
    authenticateUser();
  };
  const captchaEnableComponents =
    openIDConnectService.getEsignetConfiguration(
      configurationKeys.captchaEnableComponents
    ) ?? process.env.REACT_APP_CAPTCHA_ENABLE;

  const captchaEnableComponentsList = captchaEnableComponents
    .split(",")
    .map((x) => x.trim().toLowerCase());

  const [showCaptcha, setShowCaptcha] = useState(
    captchaEnableComponentsList.indexOf("pwd") !== -1
  );

  const captchaSiteKey =
    openIDConnectService.getEsignetConfiguration(
      configurationKeys.captchaSiteKey
    ) ?? process.env.REACT_APP_CAPTCHA_SITE_KEY;

  const [captchaToken, setCaptchaToken] = useState(null);
  const _reCaptchaRef = useRef(null);
  const handleCaptchaChange = (value) => {
    setCaptchaToken(value);
  };

  /**
   * Reset the captcha widget
   * & its token value
   */
  const resetCaptcha = () => {
    _reCaptchaRef.current.reset();
    setCaptchaToken(null);
  }

  //Handle Login API Integration here
  const authenticateUser = async () => {
    try {
      let transactionId = openIDConnectService.getTransactionId();

      let uin = fields[0].prefix + loginState["Password_mosip-uin"] + fields[0].postfix;
      let challengeType = challengeTypes.pwd;
      let challenge = loginState["Password_password"];
      let challengeFormat = challengeFormats.pwd;

      let challengeList = [
        {
          authFactorType: challengeType,
          challenge: challenge,
          format: challengeFormat,
        },
      ];

      setStatus(states.LOADING);

      const authenticateResponse = await post_AuthenticateUser(
        transactionId,
        uin,
        challengeList,
        captchaToken
      );

      setStatus(states.LOADED);

      const { response, errors } = authenticateResponse;
      
      if (errors != null && errors.length > 0) {
        
        let errorCodeCondition = langConfig.errors.password[errors[0].errorCode] !== undefined && langConfig.errors.password[errors[0].errorCode] !== null;

        if (errorCodeCondition) {
          setErrorBanner({
            errorCode: `password.${errors[0].errorCode}`,
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
        errorCode: "password.auth_failed",
        show: true
      });
      setStatus(states.ERROR);
      if (showCaptcha) {
        resetCaptcha();
      }
    }
  };

  useEffect(() => {
    let loadComponent = async () => {
      i18n.on("languageChanged", () => {
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

  useEffect(() => {
    setInvalidState(!Object.values(loginState).every((value) => value?.trim()));
  }, [loginState]);

  const onCloseHandle = () => {
    setErrorBanner(null);
  };

  const onBlurChange = (e, errors) => {
    let id = e.target.id;
    let tempError = inputErrorBanner.map(_ => _);
    if (errors.length > 0) {
      tempError.push(id)
    } else {
      let errorIndex = tempError.findIndex(_ => _ === id);
      if (errorIndex !== -1) {
        tempError.splice(errorIndex, 1);
      }
    }
    setInputErrorBanner(tempError);
  };

  const handleForgotPassword = () => {
    window.onbeforeunload = null
  }

  return (
    <>
      <div className="grid grid-cols-8 items-center">
        {backButtonDiv}
      </div>

      {errorBanner !== null && (
        <ErrorBanner
          showBanner={errorBanner.show}
          errorCode={t2(errorBanner.errorCode)}
          onCloseHandle={onCloseHandle}
          bannerCloseTimer={bannerCloseTimer}
        />
      )}

      <form className="mt-6 space-y-6" onSubmit={handleSubmit}>
        {fields.map((field) => (
          <div className="-space-y-px">
            <InputWithImage
              key={"Password_" + field.id}
              handleChange={handleChange}
              blurChange={onBlurChange}
              value={loginState["Password_" + field.id]}
              labelText={t1(field.labelText)}
              labelFor={field.labelFor}
              id={"Password_" + field.id}
              name={field.name}
              type={field.type}
              isRequired={field.isRequired}
              placeholder={t1(field.placeholder)}
              customClass={inputCustomClass}
              imgPath={null}
              icon={field.infoIcon}
              prefix={field.prefix}
              errorCode={field.errorCode}
              maxLength={field.maxLength}
              regex={field.regex}
            />
          </div>
        ))}

        {forgotPassword && 
          <a className="forgot-password-hyperlink" id="forgot-password-hyperlink" href={forgotPasswordURL} onClick={() => handleForgotPassword()} target="_self">{t1("forgot_password")}</a>
        }

        {showCaptcha && (
          <div className="block password-google-reCaptcha">
            <ReCAPTCHA
              hl={i18n.language}
              ref={_reCaptchaRef}
              onChange={handleCaptchaChange}
              sitekey={captchaSiteKey}
              className="flex place-content-center"
            />
          </div>
        )}

        <FormAction
          type={buttonTypes.submit}
          text={t1("login")}
          id="verify_password"
          disabled={
            invalidState ||
            (inputErrorBanner && inputErrorBanner.length > 0) ||
            (showCaptcha && captchaToken === null)
          }
        />
      </form>
      {status === states.LOADING && (
        <div className="mt-2">
          <LoadingIndicator size="medium" message="authenticating_msg" />
        </div>
      )}
    </>
  );
}
