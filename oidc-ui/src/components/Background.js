import React, { useState, useEffect } from "react";
import { useTranslation } from "react-i18next";
import { configurationKeys } from "../constants/clientConstants";

export default function Background({
  heading,
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

  const backgroundLogo = process.env.REACT_APP_BACKGROUND_LOGO === "true";
  const sectionClass = process.env.REACT_APP_FOOTER === "true" ? "flexible-header-footer" : "flexible-header-only";
  
  return (
    <>
    {/* height is used by subtracting navbar height  */}
      <section className={"text-gray-600 pt-4 body-font section-background "+sectionClass}>
        <div className="container justify-center flex mx-auto px-5 sm:flex-row flex-col">
          {backgroundLogo && (<div className="flex justify-center m-10 lg:mt-20 mb:mt-0 lg:w-1/2 md:w-1/2 md:block sm:w-1/2 sm:block hidden w-5/6 mt-20 mb-10 md:mb-0">
            <img
              className="background-logo object-contain rtl:scale-x-[-1]"
              alt={t("backgroud_image_alt")}
            />
          </div>)}
          <div className="rounded shadow-lg py-4 w-full md:w-3/6 sm:w-1/2 sm:max-w-sm bg-white">
            <div className="flex flex-col flex-grow lg:px-5 md:px-4 sm:px-3 px-3">
              <div className="w-full">
                <h1 className="flex text-center justify-center title-font sm:text-base text-base mb-3 font-medium text-gray-900">
                  {heading}
                </h1>
              </div>
              <div className="w-full flex mb-4 justify-center items-center">
                <img
                  className="h-20 w-32 object-contain"
                  src={clientLogoPath}
                  alt={clientName}
                />
                <span className="text-5xl flex mx-5">&#8651;</span>
                <img
                  className="h-20 w-32 object-contain brand-only-logo"
                  alt={t("logo_alt")}
                />
              </div>
              <div
                className="h-5 text-black -mx-5 mb-2"
                style={{
                  backgroundImage:
                    "linear-gradient(0deg, #FFFFFF 0%, #F7FCFF 100%)",
                }}
              ></div>
              {component}
            </div>
            {/* Enable the signup banner when it is true in the signup.config of oauth-details */}
            {signupBanner && 
            <div className="text-center bg-[#FFF9F0] py-3 relative top-4 mt-2">
              <p className="text-sm">{t("noAccount")}</p>
              <a className="text-[#2F8EA3] hover:text-sky-600 focus:text-sky-600 font-semibold" href={signupURL} target="_self">{t("signup_with_one_login")}</a>
            </div>}
          </div>
        </div>
      </section>
    </>
  );
}
