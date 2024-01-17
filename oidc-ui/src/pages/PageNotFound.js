import React from "react";
import { buttonTypes } from "../constants/clientConstants";
import FormAction from "../components/FormAction";
import { useTranslation } from "react-i18next";

export default function PageNotFoundPage() {
  const { t } = useTranslation("translation");

  const handleResetPassword = () => {
    console.log("Handle reset password");
  };
  
  const handleRegister = () => {
    console.log("Handle register");
  };
  
  return (
    <div className="multipurpose-login-card w-full m-16 shadow shadow-lg py-24">
      <img
        className="mx-auto my-0"
        src="images/under_construction.svg"
        alt="page_not_found"
      />
      <div className="error-page-header">{t("errors.page_not_exist")}</div>
      <div className="error-page-detail">{t("errors.navigate_option")}</div>
      <div className="flex pt-8 px-8 justify-center">
        <div className="flex flex-row w-2/4 gap-x-2">
          <FormAction
            type={buttonTypes.cancel}
            text={t("password.reset_password")}
            handleClick={handleResetPassword}
            id="reset-password"
          />
          <FormAction
            type={buttonTypes.button}
            text={t("signInOption.register")}
            handleClick={handleRegister}
            id="register"
          />
        </div>
      </div>
    </div>
  );
}
