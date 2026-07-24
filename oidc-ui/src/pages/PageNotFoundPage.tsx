import { IMAGES } from "../constants/public-assets";
import { useAppTranslation } from "../hooks/useAppTranslation";

export default function PageNotFoundPage() {
  const { t } = useAppTranslation();

  return (
    <div
      className="multipurpose-login-card w-full m-0 sm:shadow py-24 sm:mx-16 sm:my-8 sm:min-h-[80vh] section-background flex flex-col justify-center items-center"
      style={{ boxShadow: "0px 2px 5px #0000001A" }}
    >
      <img
        className="mx-auto my-0"
        src={IMAGES.UNDER_CONSTRUCTION}
        alt="page_not_found"
      />
      <div className="error-page-header">{t("errors.page_not_found")}</div>
    </div>
  );
}
