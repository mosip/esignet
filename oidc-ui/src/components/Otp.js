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
  backButtonDiv,
  i18nKeyPrefix = "otp",
}) {
  const { t } = useTranslation("translation", { keyPrefix: i18nKeyPrefix });

  const [otpStatus, setOtpStatus] = useState(OTPStatusEnum.getOtp);
  const [otpResponse, setOtpResponse] = useState("");
  const [captchaToken, setCaptchaToken] = useState(null);
  const [vid, setVid] = useState("");

  const onOtpSent = async (vid, response) => {
    setOtpResponse(response);
    setVid(vid);
    setOtpStatus(OTPStatusEnum.verifyOtp);
  };

  return (
    <>
      <div className="grid grid-cols-8 items-center">
        {otpStatus === OTPStatusEnum.verifyOtp ? (
          <div className="h-6 items-center text-center flex items-start">
            <button
              id="back-button"
              onClick={() => setOtpStatus(OTPStatusEnum.getOtp)}
              className="text-2xl font-semibold justify-left rtl:rotate-180 back-button-color"
            >
              &#8592;
            </button>
          </div>
        ) : (
          backButtonDiv
        )}
      </div>

      {otpStatus === OTPStatusEnum.getOtp && (
        <OtpGet
          param={param}
          authService={authService}
          openIDConnectService={openIDConnectService}
          onOtpSent={onOtpSent}
          getCaptchaToken={(value) => {
            setCaptchaToken(value);
          }}
        />
      )}

      {otpStatus === OTPStatusEnum.verifyOtp && (
        <OtpVerify
          param={param}
          otpResponse={otpResponse}
          vid={vid}
          authService={authService}
          openIDConnectService={openIDConnectService}
          captcha={captchaToken}
        />
      )}
    </>
  );
}
