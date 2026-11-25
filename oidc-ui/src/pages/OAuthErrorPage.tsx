import { IMAGES } from "../constants/public-assets";
import { useAppTranslation } from "../hooks/useAppTranslation";

export default function OAuthErrorPage() {
  const { t } = useAppTranslation();

  return (
    <div
      className="multipurpose-login-card w-full m-0 sm:shadow py-24 sm:mx-16 sm:my-8 sm:min-h-[80vh] section-background flex flex-col justify-center items-center"
      style={{ boxShadow: "0px 2px 5px #0000001A" }}
    >
      <img
        className="mx-auto my-0"
        src={IMAGES.UNDER_CONSTRUCTION}
        alt="login_failed"
      />
      <div className="error-page-header px-4">{t("errors.login_failed.title")}</div>
      <div className="error-page-detail max-w-md px-4">
        {t("errors.login_failed.description")}
      </div>
    </div>
  );
}
