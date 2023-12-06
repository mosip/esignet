import { useTranslation } from "react-i18next";
import { useEffect, useState } from "react";
import { LoadingStates as states } from "../constants/states";
import configService from "../services/configService";

const config = await configService();

export default function EsignetDetails({ i18nKeyPrefix = "esignetDetails" }) {
  const { t } = useTranslation("translation", { keyPrefix: i18nKeyPrefix });

  const [status, setStatus] = useState({ state: states.LOADED, msg: "" });
  const [details, setDetails] = useState(null);

  useEffect(() => {
    setStatus({ state: states.LOADING, msg: t("loading_msg") });

    // if the environment is not passed then this will assigned as empty list
    let detailList = [];
    try {
      detailList = JSON.parse(decodeURIComponent(window._env_.DEFAULT_WELLKNOWN));
    } catch (error) {
      console.error("Default Wellknown Endpoint is not a valid JSON");
    }
    
    setDetails(detailList);
    setStatus({ state: states.LOADED, msg: "" });
  }, []);

  // to open a well known endpoint in a separate blank tab
  const openWellKnownEndpoint = (endpoint) => {
    window.open(
      process.env.PUBLIC_URL + endpoint,
      "_blank",
      "noopener,noreferrer"
    );
  };

  // check if background logo is needed or not,
  // create div according to the environment variable
  const backgroundLogoDiv =
    config["background_logo"] ? (
      <div className="flex justify-center m-10 lg:mt-20 mb:mt-0 lg:w-1/2 md:w-1/2 md:block sm:w-1/2 sm:block hidden w-5/6 mt-20 mb-10 md:mb-0">
        <img
          className="background-logo object-contain rtl:scale-x-[-1]"
          alt={t("backgroud_image_alt")}
        />
      </div>
    ) : (
      <>
        <img className="top_left_bg_logo" alt="top left background" />
        <img className="bottom_left_bg_logo" alt="bottom right background" />
      </>
    );
  return (
    <>
      <section className="text-gray-600 body-font">
        <div className="container justify-center flex mx-auto px-5 sm:flex-row flex-col">
          {backgroundLogoDiv}
          <div className="lg:flex-grow md:w-1/2 flex flex-col md:items-start text-left items-center">
            <div className="w-full flex justify-center">
              <img className="mb-4 h-20 brand-only-logo" />
            </div>
            <div className="w-full">
              <h1 className="flex justify-center title-font sm:text-3xl text-3xl mb-16 font-medium text-gray-900">
                {t("esignet_details_heading")}
              </h1>
            </div>
            <div className="w-full flex justify-center">
              {status.state === states.LOADED && details && (
                <div className={"w-3/4 h-min shadow-lg rounded bg-[#F8F8F8]"}>
                  <div className="py-3">
                    <div className="divide-y-2 gap-2">
                      {details.map((detail, idx) => (
                        <div className="px-2 py-1 grid grid-cols-3">
                          <div className="col-span-1 flex justify-start">
                            {detail.icon && <img src={detail.icon} />}
                            {!detail.icon && detail.name}
                          </div>
                          <div
                            className="col-span-2 flex justify-start cursor-pointer"
                            onClick={() => openWellKnownEndpoint(detail.value)}
                          >
                            {detail.value}
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>
      </section>
    </>
  );
}
