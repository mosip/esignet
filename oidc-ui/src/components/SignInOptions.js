import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import LoadingIndicator from "../common/LoadingIndicator";
import { configurationKeys } from "../constants/clientConstants";
import { LoadingStates as states } from "../constants/states";
import { getAllAuthFactors } from "../services/utilService";

export default function SignInOptions({
  openIDConnectService,
  handleSignInOptionClick,
  i18nKeyPrefix = "signInOption",
}) {
  const { t } = useTranslation("translation", { keyPrefix: i18nKeyPrefix });

  const [status, setStatus] = useState({ state: states.LOADED, msg: "" });
  const [singinOptions, setSinginOptions] = useState(null);
  const [showMoreOptions, setShowMoreOptions] = useState(false);

  const boxStyle = {
    background: "#FFFFFF 0% 0% no-repeat padding-box",
    border: "1px solid #BCC0C7",
    borderRadius: "6px",
    opacity: 1,
  };

  useEffect(() => {
    setStatus({ state: states.LOADING, msg: "loading_msg" });

    let oAuthDetails = openIDConnectService.getOAuthDetails();
    let authFactors = oAuthDetails?.authFactors;

    let wlaList =
      openIDConnectService.getEsignetConfiguration(
        configurationKeys.walletConfig
      ) ?? process.env.REACT_APP_WALLET_CONFIG;

    let loginOptions = getAllAuthFactors(authFactors, wlaList);
    console.log({ loginOptions });

    setSinginOptions(loginOptions);
    setShowMoreOptions(loginOptions.length > 4 && loginOptions.length !== 5);
    setStatus({ state: states.LOADED, msg: "" });
  }, []);

  return (
    <>
      <h1 className="text-base leading-5 font-sans font-medium mb-5">
        {t("preferred_mode_of_login")}
      </h1>

      {status.state === states.LOADING && (
        <div>
          <LoadingIndicator size="medium" message={status.msg} />
        </div>
      )}

      {status.state === states.LOADED && singinOptions && (
        <div className="grid grid-rows-7 w-full flex rounded">
          {singinOptions
            .slice(0, showMoreOptions ? 4 : undefined)
            .map((option, idx) => (
              <div
                key={idx}
                className="w-full flex py-2 px-1 my-1 cursor-pointer"
                style={boxStyle}
                id={option.id}
                onClick={(e) => handleSignInOptionClick(option.value)}
              >
                <img
                  className="mx-2 h-6 w-6"
                  src={option.icon}
                  alt={option.id}
                />
                <div className="font-medium truncate ltr:text-left rtl:text-right ltr:ml-1.5 rtl:mr-1.5">
                  {t("login_with", {
                    option: t(option.label, option.label),
                  })}
                </div>
              </div>
            ))}
        </div>
      )}

      {showMoreOptions && (
        <div
          className="text-center font-medium text-[#0953FA]"
          onClick={() => setShowMoreOptions(false)}
        >
          {t("more_ways_to_sign_in")}
          {/* <span className="mx-2 text-[#0953FA]">{"\u2304"}</span>
          <span className="mx-2 text-[#0953FA]">{"\u02C5"}</span> */}
          <span className="mx-2 text-[#0953FA] font-[100  0] text-xs">{"\u22C1"}</span>
          
        </div>
      )}
    </>
  );
}
