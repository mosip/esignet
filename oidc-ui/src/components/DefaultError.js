import { useTranslation } from "react-i18next";
import ErrorIndicator from "../common/ErrorIndicator";

export default function DefaultError({
  errorCode,
  backgroundImgPath,
  i18nKeyPrefix = "errors",
}) {
  const { t } = useTranslation("translation", { keyPrefix: i18nKeyPrefix });

  return (
    <>
      <section className="text-gray-600 mt-4 body-font">
        <div className="container flex mx-auto px-5 md:flex-row flex-col">
          <div className="flex justify-center lg:mt-32 mt-20 mb:mt-0 lg:w-1/2 md:w-1/2 w-5/6 mb-10 md:mb-0">
            <img
              className="object-contain rtl:scale-x-[-1]"
              alt={t("backgroud_image_alt")}
              src={backgroundImgPath}
            />
          </div>
          <div>
            <div className="lg:flex-grow lg:px-24 md:px-16 flex flex-col pt-36">
              <div className="flex justify-center items-center min-h-[340px] flex shadow-lg rounded bg-[#F8F8F8]">
                <div className="w-96 px-5">
                  <ErrorIndicator errorCode={errorCode} />
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>
    </>
  );
}
