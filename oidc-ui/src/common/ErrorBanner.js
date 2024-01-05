import { useTranslation } from "react-i18next";

const ErrorBanner = ({
  showBanner,
  errorCode,
  onCloseHandle,
  customClass = "",
  bannerCloseTimer,
}) => {
  const { t } = useTranslation("translation");

  if (bannerCloseTimer) {
    setTimeout(onCloseHandle, bannerCloseTimer * 1000);
  }

  return showBanner && (
    <div
      className={
        "flex justify-between items-center px-2 sm:px-5 lg:-mx-5 md:-mx-4 sm:-mx-3 -mx-3 error-banner " +
        customClass
      }
    >
      <div className="error-banner-text text-sm font-semibold">{t(errorCode)}</div>
      <img
        onClick={onCloseHandle}
        className="h-2.5 w-2.5 hover:cursor-pointer"
        src="images/cross_icon.svg"
      />
    </div>
  );
};

export default ErrorBanner;
