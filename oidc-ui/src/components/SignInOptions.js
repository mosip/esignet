import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import LoadingIndicator from "../common/LoadingIndicator";
import { configurationKeys } from "../constants/clientConstants";
import { LoadingStates as states } from "../constants/states";
import { getAllAuthFactors } from "../services/walletService";
import { Buffer } from "buffer";

export default function SignInOptions({
  openIDConnectService,
  handleSignInOptionClick,
  localStorageService,
  i18nKeyPrefix = "signInOption",
}) {
  const { i18n, t } = useTranslation("translation", {
    keyPrefix: i18nKeyPrefix,
  });

  const [status, setStatus] = useState({ state: states.LOADED, msg: "" });
  const [singinOptions, setSinginOptions] = useState(null);
  const [showMoreOptions, setShowMoreOptions] = useState(false);
  const [crossBorderSinginOptions, setCrossBorderSinginOptions] =
    useState(null);

  useEffect(() => {
    setStatus({ state: states.LOADING, msg: "loading_msg" });

    let oAuthDetails = openIDConnectService.getOAuthDetails();
    let authFactors = oAuthDetails?.authFactors;

    let wlaList = openIDConnectService.getEsignetConfiguration(
      configurationKeys.walletConfig
    );

    let crossBorderConfigs = openIDConnectService.getEsignetConfiguration(
      configurationKeys.crossBorderAuthConfig
    );

    let loginOptions = getAllAuthFactors(authFactors, wlaList);

    let options = getCrossBorderLoginOptions(crossBorderConfigs);
    setCrossBorderSinginOptions(options);
    setSinginOptions(loginOptions);
    setShowMoreOptions(loginOptions.length > 4 && loginOptions.length !== 5);
    setStatus({ state: states.LOADED, msg: "" });

    i18n.on("languageChanged", function (lng) {
      renderSignInButton(options);
    });
  }, []);

  const getCrossBorderLoginOptions = (crossBorderConfigs) => {
    let crossBorderLoginOptions = [];
    crossBorderConfigs?.forEach((crossBorderConfig) => {
      let buttonConfig = {
        ...crossBorderConfig.buttonConfig,
        labelText: t("sign_in_with", {
          idProviderName: crossBorderConfig.idProviderName,
        }),
      };

      let authorizeRequest = localStorageService.getAuthorizeRequest();

      //space seperated values
      const encodedState = Buffer.from(
        openIDConnectService.getTransactionId() +
          " " +
          openIDConnectService.getRedirectUri()
      ).toString("base64");

      let claims_locales = authorizeRequest.claims_locales ?? "";

      if (crossBorderConfig.claims_locales)
        claims_locales = (
          claims_locales +
          " " +
          crossBorderConfig.claims_locales
        ).trim();

      let ui_locales = i18n.language;
      let claims = authorizeRequest.claims;
      let response_type = authorizeRequest.response_type;
      let scope = authorizeRequest.scope;
      let nonce = authorizeRequest.nonce;

      let oidcConfig = {
        ...crossBorderConfig.oidcConfig,
        state: encodedState,
        claims_locales,
        ui_locales,
        claims,
        response_type,
        scope,
        nonce,
      };

      crossBorderLoginOptions.push({
        oidcConfig: oidcConfig,
        buttonConfig: buttonConfig,
        idProviderName: crossBorderConfig.idProviderName,
      });
    });
    return crossBorderLoginOptions;
  };

  useEffect(() => {
    renderSignInButton(crossBorderSinginOptions);
  }, [crossBorderSinginOptions]);

  const renderSignInButton = (crossBorderSinginOptions) => {
    crossBorderSinginOptions?.forEach((option) => {
      let buttonConfig = {
        ...option.buttonConfig,
        labelText: t("sign_in_with", {
          idProviderName: option.idProviderName,
        }),
      };

      window.SignInWithEsignetButton?.init({
        oidcConfig: option.oidcConfig,
        buttonConfig: buttonConfig,
        signInElement: document.getElementById(option.idProviderName),
      });
    });
  };

  return (
    <>
      <h1 className="text-base leading-5 font-sans font-medium mb-5">
        {t("preferred_mode_of_login")}
      </h1>

      {status.state === states.LOADING && (
        <div>
          <LoadingIndicator size="medium" message={status.msg} />
        </div>
      )}

      {status.state === states.LOADED && singinOptions && (
        <div className="grid grid-rows-7 w-full flex rounded">
          {singinOptions
            .slice(0, showMoreOptions ? 4 : undefined)
            .map((option, idx) => (
              <div
                key={idx}
                className="w-full flex py-2 px-1 my-1 cursor-pointer login-list-box-style"
                id={option.id}
                onClick={(e) => handleSignInOptionClick(option.value)}
              >
                <img
                  className="mx-2 h-6 w-6"
                  src={option.icon}
                  alt={option.id}
                />
                <div className="font-medium truncate ltr:text-left rtl:text-right ltr:ml-1.5 rtl:mr-1.5">
                  {t("login_with", {
                    option: t(option.label, option.label),
                  })}
                </div>
              </div>
            ))}
        </div>
      )}

      {showMoreOptions && (
        <div
          className="text-center cursor-pointer font-medium text-[#0953FA] mt-3 flex flex-row rtl:flex-row-reverse items-center justify-center"
          onClick={() => setShowMoreOptions(false)}
        >
          <span className="mr-2 rtl:ml-2">{t("more_ways_to_sign_in")}</span>
          <span>
            <svg
              xmlns="http://www.w3.org/2000/svg"
              height="1em"
              viewBox="0 0 512 512"
            >
              <path
                className="fill-[#0953FA]"
                d="M233.4 406.6c12.5 12.5 32.8 12.5 45.3 0l192-192c12.5-12.5 12.5-32.8 0-45.3s-32.8-12.5-45.3 0L256 338.7 86.6 169.4c-12.5-12.5-32.8-12.5-45.3 0s-12.5 32.8 0 45.3l192 192z"
              />
            </svg>
          </span>
        </div>
      )}

      {status.state === states.LOADED &&
        window.SignInWithEsignetButton &&
        crossBorderSinginOptions &&
        crossBorderSinginOptions.length > 0 && (
          <>
            <div className="flex w-full my-2 items-center px-5">
              <div className="flex-1 h-px bg-zinc-400" />
              <div>
                <p className="w-14 text-center">{t("or")}</p>
              </div>
              <div className="flex-1 h-px bg-zinc-400" />
            </div>
            <div className="w-full">
              <p className="w-full text-center font-medium">
                Verify using National IDs
              </p>
            </div>
            <div className="flex justify-center w-full">
              {crossBorderSinginOptions.map((option) => (
                <div key={option.idProviderName} className="my-4 mx-2">
                  <div id={option.idProviderName}></div>
                </div>
              ))}
            </div>
          </>
        )}
    </>
  );
}
