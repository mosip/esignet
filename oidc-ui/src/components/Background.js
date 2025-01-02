import React, { useState, useEffect } from "react";
import { useTranslation } from "react-i18next";
import { configurationKeys } from "../constants/clientConstants";

export default function Background({
  heading,
  subheading,
  clientLogoPath,
  clientName,
  component,
  oidcService,
  authService,
  i18nKeyPrefix = "header",
}) {
  const { t, i18n } = useTranslation("translation", {
    keyPrefix: i18nKeyPrefix,
  });

  const [signupBanner, setSignupBanner] = useState(false);
  const [signupURL, setSignupURL] = useState("");

  let signupConfig = oidcService.getEsignetConfiguration(
    configurationKeys.signupConfig
  );

  useEffect(() => {
    if (signupConfig?.[configurationKeys.signupBanner]) {
      setSignupBanner(true);
      setSignupURL(
        signupConfig[configurationKeys.signupURL] +
          "#" +
          authService.getAuthorizeQueryParam()
      );
    }
    // document.getElementById("language_dropdown")?.style?.display="none"
  }, [i18n.language]);

  // check signup banner is present or not,
  // and padding according to that only
  const conditionalPadding = signupBanner ? "pt-4" : "py-4";

  const handleSignup = () => {
    window.onbeforeunload = null;
  };
  return (
    <div
      className={
        "multipurpose-login-card shadow w-full md:w-3/6 lg:max-w-sm md:z-10 m-0 md:m-auto " +
        conditionalPadding
      }
    >
      <div className="flex flex-col flex-grow lg:px-5 md:px-4 sm:px-3 px-3">
        <div className="w-full py-1">
          <h1
            className="flex text-center justify-center mb-3 font-bold text-xl"
            id="login-header"
          >
            {heading}
          </h1>
          {clientName && (
            <h1
              className="flex text-center justify-center title-font sm:text-base text-base mb-3 py-1 font-small"
              id="login-subheader"
            >
              {t("subheading", {
                clientName: subheading["@none"],
              })}
            </h1>
          )}
        </div>
        <div className="w-full flex mb-4 justify-center items-center pb-2">
          {clientLogoPath && (
            <img
              className="object-contain client-logo-size client-logo-shadow rounded-[25px] border-[0.1px] border-white"
              src={clientLogoPath}
              alt={clientName}
            />
          )}
          <span className="flex mx-5 alternate-arrow"></span>
          <img
            className="object-contain brand-only-logo client-logo-size"
            alt={t("logo_alt")}
          />
        </div>
        <div className="text-black lg:-mx-5 md:-mx-4 sm:-mx-3 -mx-3 login-card-separator"></div>
        {component}
      </div>
      {/* Enable the signup banner when it is true in the signup.config of oauth-details */}
      {signupBanner && (
        <div className="signup-banner">
          <p className="signup-banner-text" id="no-account">
            {t("noAccount")}
          </p>
          <a
            className="signup-banner-hyperlink"
            id="signup-url-button"
            href={signupURL}
            target="_self"
            onClick={() => handleSignup()}
          >
            {t("signup_for_unified_login")}
          </a>
        </div>
      )}
    </div>
  );
}
