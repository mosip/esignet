import React from "react";
import { useTranslation } from "react-i18next";

export default function PageNotFoundPage({ i18nKeyPrefix = "errors" }) {
  const { t } = useTranslation("translation", { keyPrefix: i18nKeyPrefix });

  return (
    <div className="multipurpose-login-card w-full m-0 sm:shadow sm:shadow-lg py-24 sm:m-16 section-background">
      <img
        className="mx-auto my-0"
        src="images/under_construction.svg"
        alt="page_not_found"
      />
      <div className="error-page-header">{t("page_not_exist")}</div>
      <div className="error-page-detail">{t("navigate_option")}</div>
    </div>
  );
}
