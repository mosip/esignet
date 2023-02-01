import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import ErrorIndicator from "../common/ErrorIndicator";
import LoadingIndicator from "../common/LoadingIndicator";
import { buttonTypes, challengeFormats, challengeTypes } from "../constants/clientConstants";
import { otpFields } from "../constants/formFields";
import { LoadingStates as states } from "../constants/states";
import FormAction from "./FormAction";
import Input from "./Input";

const fields = otpFields;
let fieldsState = {};
fields.forEach((field) => (fieldsState["Pin" + field.id] = ""));

export default function Pin({
  param,
  authService,
  openIDConnectService,
  i18nKeyPrefix = "pin",
}) {
  const { t } = useTranslation("translation", { keyPrefix: i18nKeyPrefix });

  const fields = param;
  const post_AuthenticateUser = authService.post_AuthenticateUser;

  const [loginState, setLoginState] = useState(fieldsState);
  const [error, setError] = useState(null);
  const [status, setStatus] = useState(states.LOADED);

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
          format: challengeFormat
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
      setStatus(states.ERROR);
    }
  };

  return (
    <>
      <h1 className="text-center text-sky-600 font-semibold line-clamp-2" title={t("sign_in_with_pin")}>
        {t("sign_in_with_pin")}
      </h1>
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
              placeholder={t(field.placeholder)}
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
              {t("remember_me")}
            </label>
          </div>
        </div>
        <FormAction type={buttonTypes.submit} text={t("login")} />
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
