import { useState } from "react";
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

const fields = passwordFields;
let fieldsState = {};
fields.forEach((field) => (fieldsState["Password" + field.id] = ""));

export default function Password({
  param,
  authService,
  openIDConnectService,
  handleBackButtonClick,
  i18nKeyPrefix = "password",
}) {
  const { t } = useTranslation("translation", { keyPrefix: i18nKeyPrefix });

  const fields = param;
  const post_AuthenticateUser = authService.post_AuthenticateUser;
  const buildRedirectParams = authService.buildRedirectParams;

  const [loginState, setLoginState] = useState(fieldsState);
  const [error, setError] = useState(null);
  const [status, setStatus] = useState(states.LOADED);

  const passwordRegexValue =
    openIDConnectService.getEsignetConfiguration(
      configurationKeys.passwordRegex
    ) ?? process.env.REACT_APP_PASSWORD_REGEX;

  const passwordRegex = new RegExp(passwordRegexValue);

  const navigate = useNavigate();

  const handleChange = (e) => {
    setLoginState({ ...loginState, [e.target.id]: e.target.value });
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    authenticateUser();
  };

  //Handle Login API Integration here
  const authenticateUser = async () => {
    try {
      let transactionId = openIDConnectService.getTransactionId();

      let uin = loginState["Password_mosip-uin"];
      let challengeType = challengeTypes.pwd;
      let challenge = loginState["Password_password"];
      let challengeFormat = challengeFormats.pwd;

      if (!passwordRegex.test(challenge)) {
        setError({
          defaultMsg: "Password Invalid",
          errorCode: "password_error_msg",
        });
        return;
      }

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
        challengeList
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

  return (
    <>
      <div className="grid grid-cols-8 items-center">
        <div className="h-6 items-center text-center flex items-start">
          <button
            onClick={() => handleBackButtonClick()}
            className="text-sky-600 text-2xl font-semibold justify-left rtl:rotate-180"
          >
            &#8592;
          </button>
        </div>
        <div className="h-6 flex justify-center col-start-2 col-span-6 h-fit">
          <h1
            className="text-center text-sky-600 font-semibold line-clamp-2"
            title={t("sign_in_with_password")}
          >
            {t("sign_in_with_password")}
          </h1>
        </div>
      </div>

      <form className="mt-8 space-y-6" onSubmit={handleSubmit}>
        {fields.map((field) => (
          <div className="-space-y-px">
            <InputWithImage
              key={"Password_" + field.id}
              handleChange={handleChange}
              value={loginState["Password_" + field.id]}
              labelText={t(field.labelText)}
              labelFor={field.labelFor}
              id={"Password_" + field.id}
              name={field.name}
              type={field.type}
              isRequired={field.isRequired}
              placeholder={t(field.placeholder)}
              imgPath={
                field.type === "password" ? null : "images/photo_scan.png"
              }
            />
          </div>
        ))}

        <FormAction
          type={buttonTypes.submit}
          text={t("login")}
          id="verify_password"
        />
      </form>
      {status === states.LOADING && (
        <div>
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
