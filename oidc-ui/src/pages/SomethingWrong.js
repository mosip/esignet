import React from "react";
import { useTranslation } from "react-i18next";
import { useLocation } from "react-router-dom";

export default function SomethingWrongPage({ i18nKeyPrefix = "errors" }) {
  const { t } = useTranslation("translation", { keyPrefix: i18nKeyPrefix });
  const statusCode = useLocation().state.code;
  return (
    <div className="multipurpose-login-card w-full m-0 sm:shadow sm:shadow-lg py-24 sm:m-16 section-background">
      <img className="mx-auto my-0" src="images/under_construction.svg" alt="something_went_wrong" />
      <div className="error-page-header">{t("statusCodeHeader." + statusCode)}</div>
      <div className="error-page-detail">{t("statusCodeSubHeader." + statusCode)}</div>
    </div>
  );
}
