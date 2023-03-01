import { useState } from "react";
import { useTranslation } from "react-i18next";
import OtpGet from "./OtpGet";
import OtpVerify from "./OtpVerify";

const OTPStatusEnum = {
  getOtp: "GETOTP",
  verifyOtp: "VERIFYOTP",
};

export default function Otp({
  param,
  authService,
  openIDConnectService,
  i18nKeyPrefix = "otp",
}) {
  const { t } = useTranslation("translation", { keyPrefix: i18nKeyPrefix });

  const [otpStatus, setOtpStatus] = useState(OTPStatusEnum.getOtp);
  const [otpResponse, setOtpResponse] = useState("");
  const [vid, setVid] = useState("");

  const onOtpSent = async (vid, response) => {
    setOtpResponse(response);
    setVid(vid);
    setOtpStatus(OTPStatusEnum.verifyOtp);
  };

  return (
    <>
      <div className="grid grid-cols-8 items-center">
        {otpStatus === OTPStatusEnum.verifyOtp && (
          <div className="h-6 items-center text-center flex items-start">
            <button
              onClick={() => {
                setOtpStatus(OTPStatusEnum.getOtp);
              }}
              className="text-sky-600 text-2xl font-semibold justify-left rtl:rotate-180"
            >
              &#8701;
            </button>
          </div>
        )}
        <div className="h-6 flex justify-center col-start-2 col-span-6 h-fit">
          <h1 className="text-center text-sky-600 font-semibold line-clamp-2" title={t("sign_in_with_otp")}>
            {t("sign_in_with_otp")}
          </h1>
        </div>
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
          vid={vid}
          authService={authService}
          openIDConnectService={openIDConnectService}
        />
      )}
    </>
  );
}
