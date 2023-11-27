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
  const { t } = useTranslation("translation", { keyPrefix: i18nKeyPrefix });

  const [signupBanner, setSignupBanner] = useState(false);
  const [signupURL, setSignupURL] = useState("");

  let signupConfig = oidcService.getEsignetConfiguration(
    configurationKeys.signupConfig
  );

  useEffect(() => {
    if(signupConfig?.[configurationKeys.signupBanner]) {
      setSignupBanner(true);
      setSignupURL(signupConfig[configurationKeys.signupURL] + "#" + authService.getAuthorizeQueryParam())
    }
  }, []);

  // check if background logo is needed or not,
  // create div according to the environment variable
  const backgroundLogoDiv =
    process.env.REACT_APP_BACKGROUND_LOGO === "true" ? (
      <div className="flex justify-center m-10 lg:mt-20 mb:mt-0 lg:w-1/2 md:w-1/2 md:block sm:w-1/2 sm:block hidden w-5/6 mt-20 mb-10 md:mb-0">
        <img
          className="background-logo object-contain rtl:scale-x-[-1]"
          alt={t("backgroud_image_alt")}
        />
      </div>
    ) : (
      <>
        <span className="top_left_bg_logo" alt="top left background"></span>
        <span className="bottom_left_bg_logo" alt="bottom right background"></span>
      </>
    );
    
  // check signup banner is present or not,
  // and padding according to that only
  const conditionalPadding = signupBanner ? "pt-4" : "py-4";

  return (
    <>
      {/* height is used by subtracting navbar height  */}
      <section className="login-text pt-4 body-font section-background">
        <div className="container justify-center flex mx-auto px-5 sm:flex-row flex-col">
          {backgroundLogoDiv}
          <div
            className={
              "multipurpose-login-card shadow w-full md:w-3/6 sm:w-1/2 sm:max-w-sm " +
              conditionalPadding
            }
          >
            <div className="flex flex-col flex-grow lg:px-5 md:px-4 sm:px-3 px-3">
              <div className="w-full">
                <h1 className="flex text-center justify-center title-font sm:text-base text-base mb-3 font-medium text-gray-900">
                  {heading}
                </h1>
                <h1 className="flex text-center justify-center title-font sm:text-base text-base mb-3 font-small text-gray-400">{subheading}</h1>
              </div>
              <div className="w-full flex mb-4 justify-center items-center">
                <img
                  className="object-contain client-logo-size"
                  src={clientLogoPath}
                  alt={clientName}
                />
                <span className="flex mx-5 alternate-arrow"></span>
                <span
                  className="object-contain brand-only-logo client-logo-size"
                  alt={t("logo_alt")}
                ></span>
              </div>
              <div
                className="text-black lg:-mx-5 md:-mx-4 sm:-mx-3 -mx-3 mb-2 login-card-separator"
              ></div>
              {component}
            </div>
            {/* Enable the signup banner when it is true in the signup.config of oauth-details */}
            {signupBanner && 
            <div className="signup-banner">
              <p className="signup-banner-text">{t("noAccount")}</p>
              <a className="signup-banner-hyperlink" href={signupURL} target="_self">{t("signup_with_one_login")}</a>
            </div>}
          </div>
        </div>
      </section>
    </>
  );
}
