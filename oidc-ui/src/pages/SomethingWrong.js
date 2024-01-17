import React from "react";
import { useTranslation } from "react-i18next";

export default function SomethingWrongPage({ i18nKeyPrefix = "errors" }) {
  const { t } = useTranslation("translation", { keyPrefix: i18nKeyPrefix });
  return (
    <div className="multipurpose-login-card w-full m-16 shadow shadow-lg py-24">
      <img className="mx-auto my-0" src="images/under_construction.svg" alt="something_went_wrong" />
      <div className="error-page-header">{t("something_went_wrong")}</div>
      <div className="error-page-detail">{t("experts_working")}</div>
    </div>
  );
}
