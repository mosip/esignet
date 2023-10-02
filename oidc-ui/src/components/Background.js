import React from "react";
import { useTranslation } from "react-i18next";

export default function Background({
  heading,
  logoPath,
  clientLogoPath,
  clientName,
  backgroundImgPath,
  component,
  i18nKeyPrefix = "header",
}) {
  const { t } = useTranslation("translation", { keyPrefix: i18nKeyPrefix });
  const headerHeight = document.getElementById("navbar-header")?.offsetHeight;
  const sectionStyle = {
    height: "calc(100% - " + headerHeight + "px)",
  };

  return (
    <>
      {/* height is used by subtracting navbar height  */}
      <section
        className="text-gray-600 pt-4 body-font"
        style={sectionStyle}
      >
        <div className="container flex mx-auto px-5 sm:flex-row flex-col">
          <div className="flex justify-center m-10 lg:mt-20 mb:mt-0 lg:w-1/2 md:w-1/2 md:block sm:w-1/2 sm:block hidden w-5/6 mt-20 mb-10 md:mb-0">
            <div>
              <img
                className="object-contain rtl:scale-x-[-1]"
                alt={t("backgroud_image_alt")}
                src={backgroundImgPath}
              />
            </div>
          </div>
          <div className="rounded shadow-lg py-4 w-full md:w-3/6 sm:w-1/2 sm:max-w-sm bg-white">
            <div className="flex flex-col flex-grow lg:px-5 md:px-4 sm:px-3 px-3">
              <div className="w-full">
                <h1 className="flex text-center justify-center title-font sm:text-base text-base mb-3 font-medium text-gray-900">
                  {heading}
                </h1>
              </div>
              <div className="w-full flex mb-4 justify-center items-center">
                <img className="h-20 w-32 object-contain" src={clientLogoPath} alt={clientName} />
                <span className="text-5xl flex mx-5">&#8651;</span>
                <img className="h-20 w-32 object-contain" src={logoPath} alt={t("logo_alt")} />
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
          </div>
        </div>
      </section>
    </>
  );
}
