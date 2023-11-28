import { useTranslation } from "react-i18next";

const ErrorBanner = ({
  showBanner,
  errorCode,
  onCloseHandle,
  customClass = "",
  i18nKeyPrefix = "errors",
}) => {
  const { t } = useTranslation("translation", { keyPrefix: i18nKeyPrefix });

  return showBanner && (
    <div
      className={
        "flex justify-between items-center px-5 lg:-mx-5 md:-mx-4 sm:-mx-3 -mx-3 error-banner " +
        customClass
      }
    >
      <div className="error-banner-text">{t(errorCode)}</div>
      <img
        onClick={onCloseHandle}
        className="h-2.5 w-2.5"
        src="images/cross_icon.svg"
      />
    </div>
  );
};

export default ErrorBanner;
