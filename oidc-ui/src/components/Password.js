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
  const post_AuthenticateUser = authService.post_PasswordAuthenticate;
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

  const passwordRegexValue =
    openIDConnectService.getEsignetConfiguration(
      configurationKeys.passwordRegex
    ) ?? process.env.REACT_APP_PASSWORD_REGEX;

  const usernameRegexValue =
    openIDConnectService.getEsignetConfiguration(
      configurationKeys.usernameRegex
    ) ?? process.env.REACT_APP_USERNAME_REGEX;

  const usernamePrefix = 
    openIDConnectService.getEsignetConfiguration(
      configurationKeys.usernamePrefix
    ) ?? "";

  const usernamePostfix =
    openIDConnectService.getEsignetConfiguration(
      configurationKeys.usernamePostfix
    ) ?? "";

  const usernameInputType =
    openIDConnectService.getEsignetConfiguration(
      configurationKeys.usernameInputType
    ) ?? "";

  const usernameMaxLength =
    openIDConnectService.getEsignetConfiguration(
      configurationKeys.usernameMaxLength
    ) ?? "";

  const passwordMaxLength =
    openIDConnectService.getEsignetConfiguration(
      configurationKeys.passwordMaxLength
    ) ?? "";

  const bannerCloseTimer =
    openIDConnectService.getEsignetConfiguration(
      configurationKeys.bannerCloseTimer
    ) ?? "";

  fields[0].prefix = usernamePrefix;
  fields[0].type = usernameInputType ?? "text";

  const passwordRegex = new RegExp(passwordRegexValue);
  const usernameRegex = new RegExp(usernameRegexValue);

  const navigate = useNavigate();

  const handleChange = (e) => {
    onCloseHandle();

    const checkMaxLength = (maxLength) =>
      maxLength === "" ? true : e.target.value.length <= parseInt(maxLength);

    if (
      (e.target.name === "uin" && checkMaxLength(usernameMaxLength)) ||
      (e.target.name === "password" && checkMaxLength(passwordMaxLength))
    ) {
      setLoginState({ ...loginState, [e.target.id]: e.target.value });
    }
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

  //Handle Login API Integration here
  const authenticateUser = async () => {
    try {
      let transactionId = openIDConnectService.getTransactionId();

      let uin = usernamePrefix + loginState["Password_mosip-uin"] + usernamePostfix;
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
          let state = openIDConnectService.getState();
          let redirect_uri = openIDConnectService.getRedirectUri();

          if (!redirect_uri) {
            return;
          }

          let params = "?";

          if (errors[0].errorCode) {
            params = params + "error_description=" + errors[0].errorCode + "&";
          }

          //REQUIRED
          params = params + "state=" + state + "&";

          //REQUIRED
          params = params + "error=" + errors[0].errorCode;

          window.onbeforeunload = null;

          window.location.replace(redirect_uri + params);
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

        let params = buildRedirectParams(
          nonce,
          state,
          openIDConnectService.getOAuthDetails(),
          response.consentAction
        );

        navigate(process.env.PUBLIC_URL + "/consent" + params, {
          replace: true,
        });
      }
    } catch (error) {
      setErrorBanner({
        errorCode: "password.auth_failed",
        show: true
      });
      setStatus(states.ERROR);
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

  const onBlurChange = (e) => {
    const formId = e.target.id;
    const formValue = e.target.value;
    let bannerIndex = inputErrorBanner.findIndex((_) => _.id === e.target.id);
    let currentRegex = new RegExp("");
    let errorCode = "";
    if (formId.includes("mosip-uin")) {
      currentRegex = usernameRegex;
      errorCode = "username_not_valid";
    } else if (formId.includes("password")) {
      currentRegex = passwordRegex;
      errorCode = "password_not_valid";
    }
    let tempBanner = inputErrorBanner.map((_) => {
      return { ..._, show: true };
    });

    // checking regex matching for username & password
    if (currentRegex.test(formValue) || formValue === "") {
      // if username or password is matched
      // then remove error from inputErrorBanner
      if (bannerIndex > -1) {
        tempBanner.splice(bannerIndex, 1);
      }
    } else {
      // if username or passwors is not matched
      // with regex, then add the error
      if (bannerIndex === -1 && formValue !== "") {
        tempBanner.push({
          id: e.target.id,
          errorCode,
          show: true,
        });
      }
    }

    // setting the error in inputErrorBanner
    setInputErrorBanner(tempBanner);
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
              error={inputErrorBanner}
            />
          </div>
        ))}

        {forgotPassword && 
          <a className="forgot-password-hyperlink" href={forgotPasswordURL} onClick={() => handleForgotPassword()} target="_self">{t1("forgot_password")}</a>
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
