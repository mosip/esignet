import React, { useState } from "react";
import { useTranslation } from "react-i18next";

export default function Background({
  heading,
  logoPath,
  clientLogoPath,
  clientName,
  backgroundImgPath,
  component,
  handleMoreWaysToSignIn,
  showMoreOption,
  linkedWalletComp,
  appDownloadURI,
  qrCodeEnable,
  i18nKeyPrefix = "header",
}) {
  const tabs = [
    {
      id: "wallet_tab_id",
      name: "inji_tab_name",
    },
    {
      id: "here_tab_id",
      name: "here_tab_name",
    },
  ];

  const [openTab, setOpenTab] = useState(qrCodeEnable === true ? 0 : 1);
  const { t } = useTranslation("translation", { keyPrefix: i18nKeyPrefix });
  return (
    <>
      <section className="text-gray-600 mt-4 body-font">
        <div className="container flex mx-auto px-5 sm:flex-row flex-col rtl:sm:flex-row-reverse">
          <div className="flex justify-center lg:mt-32 mb:mt-0 lg:w-1/2 md:w-1/2 md:block sm:w-1/2 sm:block hidden w-5/6 mt-20 mb-10 md:mb-0">
            <div>
              <img
                className="object-contain rtl:scale-x-[-1]"
                alt={t("backgroud_image_alt")}
                src={backgroundImgPath}
              />
            </div>
          </div>
          <div className="rounded overflow-hidden shadow-lg py-4 w-full md:w-3/6 sm:w-1/2 sm:max-w-sm lg:ml-auto md:ml-auto">
            <div className="lg:flex-grow lg:px-5 md:px-4 sm:px-3 px-3 flex flex-col">
              <div className="w-full">
                <h1 className="flex text-center justify-center title-font sm:text-3xl text-3xl mb-3 font-medium text-gray-900">
                  {heading}
                </h1>
              </div>
              <div className="w-full flex mb-4 justify-center items-center">
                <img className="h-12" src={clientLogoPath} alt={clientName} />
                <span className="text-5xl flex mx-5">&#8651;</span>
                <img className="h-12" src={logoPath} alt={t("logo_alt")} />
              </div>
            </div>
          </div>
        </div>
      </section>
    </>
  );
}
