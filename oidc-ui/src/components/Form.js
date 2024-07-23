import { useEffect, useState, useRef } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import LoadingIndicator from "../common/LoadingIndicator";
import {
  buttonTypes,
  configurationKeys,
} from "../constants/clientConstants";
import { LoadingStates as states } from "../constants/states";
import FormAction from "./FormAction";
import InputWithImage from "./InputWithImage";
import ReCAPTCHA from "react-google-recaptcha";
import ErrorBanner from "../common/ErrorBanner";
import redirectOnError from "../helpers/redirectOnError";
import langConfigService from "../services/langConfigService";

let fieldsState = {};
const langConfig = await langConfigService.getEnLocaleConfiguration();

export default function Form({
  authService,
  openIDConnectService,
  backButtonDiv,
  i18nKeyPrefix1 = "Form",
  i18nKeyPrefix2 = "errors"
}) {
  
  const { t: t1, i18n } = useTranslation("translation", {
    keyPrefix: i18nKeyPrefix1,
  });

  const { t: t2 } = useTranslation("translation", {
    keyPrefix: i18nKeyPrefix2,
  });
  
  const inputCustomClass =
    "h-10 border border-input bg-transparent px-3 py-2 text-sm ring-offset-background file:border-0 file:bg-transparent file:text-sm file:font-medium placeholder:text-[hsla(0, 0%, 51%)] focus-visible:outline-none disabled:cursor-not-allowed disabled:bg-muted-light-gray shadow-none";

  const fields = openIDConnectService.getEsignetConfiguration(configurationKeys.authFactorKnowledgeFieldDetails) ?? [];
  fields.forEach((field) => (fieldsState["_form_" + field.id] = ""));
  const post_AuthenticateUser = authService.post_AuthenticateUser;
  const buildRedirectParams = authService.buildRedirectParams;

  const [loginState, setLoginState] = useState(fieldsState);
  const [error, setError] = useState(null);
  const [errorBanner, setErrorBanner] = useState([]);
  const [status, setStatus] = useState(states.LOADED);
  const [invalidState, setInvalidState] = useState(true);

  useEffect(() => {  
  }, []);

  const navigate = useNavigate();

  const handleChange = (e, field) => {
    const regex = new RegExp(field.regex);
    const value = e.target.value;
    
    if (e.target.type === 'text' && field?.regex !== null && field?.regex !== undefined) {
      setLoginState({ ...loginState, [e.target.id]: regex.test(value) || value === "" || value === null ? value : loginState[e.target.id] });
    }
    else {
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
    captchaEnableComponentsList.indexOf("kbi") !== -1
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
      let uin = loginState["_form_"+openIDConnectService.getEsignetConfiguration(configurationKeys.authFactorKnowledgeIndividualIdField) ?? ""];
      let challengeManipulate = {};
      fields.forEach(function(field) {
        if(field.id !== openIDConnectService.getEsignetConfiguration(configurationKeys.authFactorKnowledgeIndividualIdField)){
          challengeManipulate[field.id] = loginState["_form_"+field.id]
        }
      });
      let challenge = btoa(JSON.stringify(challengeManipulate));

      let challengeList = [
        {
          authFactorType: "KBI",
          challenge: challenge,
          format: "base64url-encoded-json",
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
        let errorCodeCondition = langConfig.errors.otp[errors[0].errorCode] !== undefined && langConfig.errors.kbi[errors[0].errorCode] !== null;

        if (errorCodeCondition) {
          setErrorBanner({
            errorCode: `kbi.${errors[0].errorCode}`,
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
        setError(null);
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
        errorCode: "kbi.auth_failed",
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
          setShowCaptcha(true);
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

  return (
    <>
      <div className="grid grid-cols-8 items-center">
      {(backButtonDiv)}
      </div>

      {errorBanner !== null && (
        <ErrorBanner
          showBanner={errorBanner.show}
          errorCode={t2(errorBanner.errorCode)}
          onCloseHandle={onCloseHandle}
        />
      )}

      <form className="mt-6 space-y-6" onSubmit={handleSubmit}>
        {fields.map((field) => (
          <div className="-space-y-px">
            <InputWithImage
              key={"_form_" + field.id}
              handleChange={(e) => {
                handleChange(e, field)
              }}
              value={loginState["_form_" + field.id]}
              labelText={t1(field.id)}
              labelFor={field.id}
              id={"_form_" + field.id}
              type={field.type}
              isRequired={true}
              placeholder={t1(field.id + "_placeholder" )}
              customClass={inputCustomClass}
              imgPath={null}
              icon={field.infoIcon}
              maxLength={field.maxLength}
            />
          </div>
        ))}

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
          id="verify_form"
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
    </>
  );
}
