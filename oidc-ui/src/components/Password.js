import { useEffect, useState, useRef } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import ErrorIndicator from "../common/ErrorIndicator";
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
import { getBooleanValue } from "../services/utilService";

const fields = passwordFields;
let fieldsState = {};
fields.forEach((field) => (fieldsState["Password_" + field.id] = ""));

export default function Password({
  param,
  authService,
  openIDConnectService,
  backButtonDiv,
  i18nKeyPrefix = "password",
}) {
  const { t, i18n } = useTranslation("translation", {
    keyPrefix: i18nKeyPrefix,
  });

  const inputCustomClass =
    "h-10 border border-input bg-transparent px-3 py-2 text-sm ring-offset-background file:border-0 file:bg-transparent file:text-sm file:font-medium placeholder:text-[hsla(0, 0%, 51%)] focus-visible:outline-none disabled:cursor-not-allowed disabled:bg-muted-light-gray shadow-none";

  const fields = param;
  const post_AuthenticateUser = authService.post_PasswordAuthenticate;
  const buildRedirectParams = authService.buildRedirectParams;

  const [loginState, setLoginState] = useState(fieldsState);
  const [error, setError] = useState(null);
  const [errorBanner, setErrorBanner] = useState([]);
  const [status, setStatus] = useState(states.LOADED);
  const [invalidState, setInvalidState] = useState(true);

  const passwordRegexValue =
    openIDConnectService.getEsignetConfiguration(
      configurationKeys.passwordRegex
    ) ?? process.env.REACT_APP_PASSWORD_REGEX;

  const usernameRegexValue =
    openIDConnectService.getEsignetConfiguration(
      configurationKeys.usernameRegex
    ) ?? process.env.REACT_APP_USERNAME_REGEX;

  const passwordRegex = new RegExp(passwordRegexValue);
  const usernameRegex = new RegExp(usernameRegexValue);

  const navigate = useNavigate();

  const handleChange = (e) => {
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

  //Handle Login API Integration here
  const authenticateUser = async () => {
    try {
      let transactionId = openIDConnectService.getTransactionId();

      let uin = loginState["Password_mosip-uin"];
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
        setError({
          prefix: "authentication_failed_msg",
          errorCode: errors[0].errorCode,
          defaultMsg: errors[0].errorMessage,
        });
        return;
      } else {
        setError(null);

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
      setError({
        prefix: "authentication_failed_msg",
        errorCode: error.message,
        defaultMsg: error.message,
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
    let tempBanner = errorBanner.map((_) => _);
    tempBanner[0].show = false;
    setErrorBanner(tempBanner);
  };

  const onBlurChange = (e) => {
    const formId = e.target.id;
    const formValue = e.target.value;
    let bannerIndex = errorBanner.findIndex((_) => _.id === e.target.id);
    let currentRegex = new RegExp("");
    let errorCode = "";
    if (formId.includes("mosip-uin")) {
      currentRegex = usernameRegex;
      errorCode = "username_not_valid";
    } else if (formId.includes("password")) {
      currentRegex = passwordRegex;
      errorCode = "password_not_valid";
    }
    let tempBanner = errorBanner.map((_) => {
      return { ..._, show: true };
    });

    // checking regex matching for username & password
    if (currentRegex.test(formValue)) {
      // if username or password is matched
      // then remove error from errorBanner
      if (bannerIndex > -1) {
        tempBanner.splice(bannerIndex, 1);
      }
    } else {
      // if username or passwors is not matched
      // with regex, then add the error
      if (bannerIndex === -1) {
        tempBanner.unshift({
          id: e.target.id,
          errorCode,
          show: true,
        });
      }
    }

    // setting the error in errorBanner
    setErrorBanner(tempBanner);
  };

  return (
    <>
      <div className="grid grid-cols-8 items-center">
        {backButtonDiv}
      </div>

      {errorBanner.length > 0 && (
        <ErrorBanner
          showBanner={errorBanner[0]?.show}
          errorCode={errorBanner[0]?.errorCode}
          onCloseHandle={onCloseHandle}
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
              labelText={t(field.labelText)}
              labelFor={field.labelFor}
              id={"Password_" + field.id}
              name={field.name}
              type={field.type}
              isRequired={field.isRequired}
              placeholder={t(field.placeholder)}
              customClass={inputCustomClass}
              imgPath={null}
              icon={field.infoIcon}
            />
          </div>
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

        <FormAction
          type={buttonTypes.submit}
          text={t("login")}
          id="verify_password"
          disabled={
            invalidState ||
            (errorBanner && errorBanner.length > 0) ||
            (showCaptcha && captchaToken === null)
          }
        />
      </form>
      {status === states.LOADING && (
        <div className="mt-2">
          <LoadingIndicator size="medium" message="authenticating_msg" />
        </div>
      )}
      {status !== states.LOADING && error && (
        <ErrorIndicator
          prefix={error.prefix}
          errorCode={error.errorCode}
          defaultMsg={error.defaultMsg}
        />
      )}
    </>
  );
}
