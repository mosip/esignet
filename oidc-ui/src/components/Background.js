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
        <div className="container flex mx-auto px-5 md:flex-row flex-col">
          <div className="flex justify-center lg:mt-32 mt-20 mb:mt-0 lg:w-1/2 md:w-1/2 w-5/6 mb-10 md:mb-0">
            <div>
              <img
                className="object-contain rtl:scale-x-[-1]"
                alt={t("backgroud_image_alt")}
                src={backgroundImgPath}
              />
            </div>
          </div>
          <div>
            <div className="lg:flex-grow lg:px-24 md:px-16 flex flex-col">
              <div className="w-full flex mb-4 justify-center items-center">
                <img
                  className="h-16"
                  src={clientLogoPath}
                  alt={clientName}
                />
                <span className="text-6xl flex mx-5">&#8651;</span>
                <img className="h-16" src={logoPath} alt={t("logo_alt")} />
              </div>
              <div className="w-full">
                <h1 className="flex text-center justify-center title-font sm:text-3xl text-3xl mb-3 font-medium text-gray-900">
                  {heading}
                </h1>
              </div>
              <div className="grid grid-rows-7 w-full flex shadow-lg rounded bg-[#F8F8F8]">
                {/* head */}
                {qrCodeEnable === true && (
                  <div className="row-span-1 w-full flex justify-start">
                    <ul
                      className="divide-dashed w-full mr-2 ml-2 mt-2 flex mb-0 list-none flex-wrap pb-1 flex-row grid grid-cols-2"
                      role="tablist"
                    >
                      {tabs.map((tab, index) => (
                        <li
                          key={tab.name + index}
                          className="-mb-px flex-auto text-center"
                        >
                          <a
                            id={tab.id}
                            className={
                              "text-xs font-bold uppercase py-2 rounded block leading-normal " +
                              (openTab === index
                                ? "shadow-lg text-white border border-transparent bg-gradient-to-t from-cyan-500 to-blue-500"
                                : "shadow-inner border border-2 text-slate-400 bg-white")
                            }
                            onClick={(e) => {
                              e.preventDefault();
                              setOpenTab(index);
                            }}
                            data-toggle="tab"
                            href="#link1"
                            role="tablist"
                          >
                            {t(tab.name)}
                          </a>
                        </li>
                      ))}
                    </ul>
                  </div>
                )}
                {/* body */}
                <div className="flex justify-center w-full">
                  <div className="row-span-5 w-96 min-h-[340px] self-start">
                    <div className="px-5 py-2">
                      <div className={openTab === 0 ? "block" : "hidden"}>
                        {qrCodeEnable === true && linkedWalletComp}
                      </div>
                      <div className={openTab === 1 ? "block" : "hidden"}>
                        {component}
                      </div>
                    </div>
                  </div>
                </div>
                {/* footer*/}
                <div className="row-span-1 mb-2">
                  <div className={openTab === 0 ? "block" : "hidden"}>
                    <p className="text-center text-black-600 font-semibold">
                      {t("dont_have_inji")}&nbsp;
                      <a href={appDownloadURI} className="text-sky-600" id="download_now">
                        {t("download_now")}
                      </a>
                    </p>
                  </div>
                  <div className={openTab === 1 ? "block" : "hidden"}>
                    <div className="flex justify-center">
                      <button
                        id="more_ways_to_sign_in"
                        className={
                          "text-gray-500 font-semibold" +
                          (showMoreOption ? " block" : " hidden")
                        }
                        onClick={handleMoreWaysToSignIn}
                      >
                        {t("more_ways_to_sign_in")}
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>
    </>
  );
}
