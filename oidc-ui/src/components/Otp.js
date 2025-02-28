import { useState } from "react";
import { useTranslation } from "react-i18next";
import OtpGet from "./OtpGet";
import OtpVerify from "./OtpVerify";
import { configurationKeys } from "../constants/clientConstants";

const OTPStatusEnum = {
  getOtp: "GETOTP",
  verifyOtp: "VERIFYOTP",
};

export default function Otp({
  param,
  authService,
  openIDConnectService,
  backButtonDiv,
  secondaryHeading,
  i18nKeyPrefix = "otp",
}) {
  const { t } = useTranslation("translation", { keyPrefix: i18nKeyPrefix });

  const [otpStatus, setOtpStatus] = useState(OTPStatusEnum.getOtp);
  const [otpResponse, setOtpResponse] = useState("");
  const [ID, setId] = useState("");
  const [currentLoginID, setCurrentLoginID] = useState("");
  const [selectedCountryOption, setSelectedCountryOption] = useState("");

  const onOtpSent = async (ID, response, loginID, selectedCountry) => {
    setId(ID);
    setOtpResponse(response);
    setOtpStatus(OTPStatusEnum.verifyOtp);
    setCurrentLoginID(loginID);
    setSelectedCountryOption(selectedCountry);
  };

  var loginIDs = openIDConnectService.getEsignetConfiguration(
    configurationKeys.loginIdOptions
  );

  if (!loginIDs || loginIDs.length === 0) {
    loginIDs = [{ id: "vid" }];
  }

  return (
    <>
      <div className="flex items-center">
        {otpStatus === OTPStatusEnum.verifyOtp ? (
          <div className="h-6 text-center flex items-start mt-2">
            <button
              id="back-button"
              onClick={() => setOtpStatus(OTPStatusEnum.getOtp)}
              className="text-2xl font-semibold justify-left rtl:rotate-180 back-button-color"
            >
              <svg
                width="15"
                height="13"
                viewBox="0 0 15 13"
                fill="none"
                xmlns="http://www.w3.org/2000/svg"
                className="mr-2 relative top-[2px]"
                aria-label="left_arrow_icon"
              >
                <g clipPath="url(#clip0_171_703)">
                  <path
                    d="M5.96463 12.0845L5.78413 11.8961L0.512572 6.39538L0.346802 6.2224L0.512572 6.04942L5.78413 0.548692L5.96463 0.360352L6.14513 0.548692L7.09357 1.53836L7.25934 1.71134L7.09357 1.88432L3.83751 5.28193H14.155H14.405V5.53193V6.91287V7.16287H14.155H3.83751L7.09357 10.5605L7.25934 10.7335L7.09357 10.9064L6.14513 11.8961L5.96463 12.0845Z"
                    fill="currentColor"
                  />
                  <path
                    d="M5.96458 11.7231L6.91302 10.7335L3.2516 6.91286H14.1549V5.53192H3.2516L6.91302 1.71133L5.96458 0.721663L0.693018 6.22239L5.96458 11.7231ZM5.96458 12.4458L0.000488281 6.22239L5.96458 -0.000976562L7.60555 1.71133L4.4233 5.03192H14.6549V7.41286H4.4233L7.60555 10.7335L5.96458 12.4458Z"
                    fill="currentColor"
                  />
                </g>
                <defs>
                  <clipPath id="clip0_171_703">
                    <rect width="14.654" height="12.447" fill="white" />
                  </clipPath>
                </defs>
              </svg>
            </button>
            <div className="inline mx-2 font-semibold relative bottom-1 ">
              {t("enter_otp")}
            </div>
          </div>
        ) : (
          backButtonDiv
        )}
        {otpStatus === OTPStatusEnum.getOtp && (
          <div className="inline mx-2 font-semibold my-3">
            {/*
              according to the login id option, secondary heading value will be changed
              if the login id option is single, then with secondary heading will pass a object with current id
              if the login id option is multiple, then secondary heading will be passed as it is
            */}
            {t(secondaryHeading, loginIDs && loginIDs.length === 1 && {
              currentID: t(loginIDs[0].id)
            })}
          </div>
        )}
      </div>

      {otpStatus === OTPStatusEnum.getOtp && (
        <OtpGet
          param={param}
          authService={authService}
          openIDConnectService={openIDConnectService}
          onOtpSent={onOtpSent}
        />
      )}

      {otpStatus === OTPStatusEnum.verifyOtp && (
        <OtpVerify
          param={param}
          otpResponse={otpResponse}
          ID={ID}
          loginID={currentLoginID}
          selectedCountry={selectedCountryOption}
          authService={authService}
          openIDConnectService={openIDConnectService}
        />
      )}
    </>
  );
}
