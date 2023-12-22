import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import LoadingIndicator from "../common/LoadingIndicator";
import {
  buttonTypes,
  challengeFormats,
  challengeTypes,
} from "../constants/clientConstants";
import { otpFields } from "../constants/formFields";
import { LoadingStates as states } from "../constants/states";
import FormAction from "./FormAction";
import Input from "./Input";
import ErrorBanner from "../common/ErrorBanner";
import langConfigService from "../services/langConfigService";

const fields = otpFields;
let fieldsState = {};
fields.forEach((field) => (fieldsState["Pin" + field.id] = ""));

const langConfig = await langConfigService.getEnLocaleConfiguration();  

export default function Pin({
  param,
  authService,
  openIDConnectService,
  backButtonDiv,
  i18nKeyPrefix1 = "pin",
  i18nKeyPrefix2 = "errors"
}) {

  const { t: t1 } = useTranslation("translation", { keyPrefix: i18nKeyPrefix1 });
  const { t: t2 } = useTranslation("translation", { keyPrefix: i18nKeyPrefix2 });

  const fields = param;
  const post_AuthenticateUser = authService.post_AuthenticateUser;
  const buildRedirectParams = authService.buildRedirectParams;

  const [loginState, setLoginState] = useState(fieldsState);
  const [status, setStatus] = useState(states.LOADED);
  const [errorBanner, setErrorBanner] = useState(null);

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

      let uin = loginState["Pin_mosip-uin"];
      let challengeType = challengeTypes.pin;
      let challenge = loginState["Pin_pin"];
      let challengeFormat = challengeFormats.pin;

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
        
        let errorCodeCondition = langConfig.errors.pin[errors[0].errorCode] !== undefined && langConfig.errors.pin[errors[0].errorCode] !== null;

        if (errorCodeCondition) {
          setErrorBanner({
            errorCode: `pin.${errors[0].errorCode}`,
            show: true
          });
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
        errorCode: "authentication_failed_msg",
        show: true
      });
      setStatus(states.ERROR);
    }
  };

  const onCloseHandle = () => {
    setErrorBanner(null);
  };

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
        />
      )}
      <form className="mt-8 space-y-6" onSubmit={handleSubmit}>
        <div className="-space-y-px">
          {fields.map((field) => (
            <Input
              key={"Pin_" + field.id}
              handleChange={handleChange}
              value={loginState["Pin_" + field.id]}
              labelText={field.labelText}
              labelFor={field.labelFor}
              id={"Pin_" + field.id}
              name={field.name}
              type={field.type}
              isRequired={field.isRequired}
              placeholder={t1(field.placeholder)}
            />
          ))}
        </div>

        <div className="flex items-center justify-between ">
          <div className="flex items-center">
            <input
              id="remember-me"
              name="remember-me"
              type="checkbox"
              className="h-4 w-4 text-cyan-600 focus:ring-cyan-500 border-gray-300 rounded"
            />
            <label
              htmlFor="remember-me"
              className="mx-2 block text-sm text-cyan-900"
            >
              {t1("remember_me")}
            </label>
          </div>
        </div>
        <FormAction
          type={buttonTypes.submit}
          text={t1("login")}
          id="verify_pin"
        />
      </form>
      {status === states.LOADING && (
        <div>
          <LoadingIndicator size="medium" message="authenticating_msg" />
        </div>
      )}
    </>
  );
}
