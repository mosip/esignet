import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import LoadingIndicator from "../common/LoadingIndicator";
import { validAuthFactors } from "../constants/clientConstants";
import { LoadingStates as states } from "../constants/states";

export default function SignInOptions({
  openIDConnectService,
  handleSignInOptionClick,
  i18nKeyPrefix = "signInOption",
}) {
  const { t } = useTranslation("translation", { keyPrefix: i18nKeyPrefix });

  const [status, setStatus] = useState({ state: states.LOADED, msg: "" });
  const [singinOptions, setSinginOptions] = useState(null);

  const modalityIconPath = {
    PIN: "images/sign_in_with_otp.png",
    OTP: "images/sign_in_with_otp.png",
    WALLET: "images/Sign in with Inji.png",
    BIO: "images/Sign in with fingerprint.png",
  };

  useEffect(() => {
    setStatus({ state: states.LOADING, msg: "loading_msg" });

    let oAuthDetails = openIDConnectService.getOAuthDetails();
    let authFactors = oAuthDetails.authFactors;

    let loginOptions = [];

    authFactors.forEach((authFactor) => {
      if (validAuthFactors[authFactor[0].type]) {
        loginOptions.push({
          label: authFactor[0].type,
          value: authFactor,
          icon: modalityIconPath[authFactor[0].type],
        });
      }
    });

    setSinginOptions(loginOptions);
    setStatus({ state: states.LOADED, msg: "" });
  }, []);

  return (
    <>
      <h1 className="text-center text-black-600 mb-10 font-bold text-lg">
        {t("login_option_heading")}
      </h1>

      {status.state === states.LOADING && (
        <div>
          <LoadingIndicator size="medium" message={status.msg} />
        </div>
      )}

      {status.state === states.LOADED && singinOptions && (
        <div className="divide-y-2">
          {singinOptions.map((option, idx) => (
            <div key={idx}>
              <button
                className="text-gray-500 font-semibold text-base"
                onClick={(e) => handleSignInOptionClick(option.value)}
              >
                <div className="flex items-center">
                  <img className="w-7" src={option.icon} />
                  <span className="ml-4 mb-4 mt-4">
                    {t("login_with", {
                      option: t(option.label),
                    })}
                  </span>
                </div>
              </button>
            </div>
          ))}
        </div>
      )}
    </>
  );
}
