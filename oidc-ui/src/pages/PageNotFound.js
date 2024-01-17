import React from "react";
import { useTranslation } from "react-i18next";

export default function PageNotFoundPage() {
  const { t } = useTranslation("translation");

  return (
    <div className="multipurpose-login-card w-full m-16 shadow shadow-lg py-24">
      <img
        className="mx-auto my-0"
        src="images/under_construction.svg"
        alt="page_not_found"
      />
      <div className="error-page-header">{t("errors.page_not_exist")}</div>
      <div className="error-page-detail">{t("errors.navigate_option")}</div>
    </div>
  );
}
