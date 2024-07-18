import i18next from "i18next";
import { useEffect, useState, useRef } from "react";
import { useTranslation } from "react-i18next";
import { Tooltip as ReactTooltip } from "react-tooltip";
import LoadingIndicator from "../common/LoadingIndicator";
import { buttonTypes, configurationKeys } from "../constants/clientConstants";
import { LoadingStates, LoadingStates as states } from "../constants/states";
import FormAction from "./FormAction";
import langConfigService from "./../services/langConfigService";
import ModalPopup from "../common/ModalPopup";
import configService from "../services/configService";
import redirectOnError from "../helpers/redirectOnError";

const config = await configService();

export default function Consent({
  authService,
  consentAction,
  authTime,
  openIDConnectService,
  i18nKeyPrefix = "consent",
}) {
  const { t } = useTranslation("translation", { keyPrefix: i18nKeyPrefix });

  const post_AuthCode = authService.post_AuthCode;

  const authenticationExpireInSec =
    openIDConnectService.getEsignetConfiguration(
      configurationKeys.consentScreenExpireInSec
    ) ?? process.env.REACT_APP_CONSENT_SCREEN_EXPIRE_IN_SEC;

  // The transaction timer will be derived from the configuration file of eSignet so buffer of -5 sec is added in the timer.
  const timeoutBuffer =
    openIDConnectService.getEsignetConfiguration(
      configurationKeys.consentScreenTimeOutBufferInSec
    ) ?? process.env.REACT_APP_CONSENT_SCREEN_TIME_OUT_BUFFER_IN_SEC;

  const transactionTimeoutWithBuffer =
    authenticationExpireInSec - timeoutBuffer;

  const firstRender = useRef(true);
  const [status, setStatus] = useState(states.LOADED);
  const [claims, setClaims] = useState([]);
  const [scope, setScope] = useState([]);
  const [clientName, setClientName] = useState("");
  const [clientLogoPath, setClientLogoPath] = useState("");
  const [claimsScopes, setClaimsScopes] = useState([]);
  const [langMap, setLangMap] = useState("");
  const [timeLeft, setTimeLeft] = useState(null);
  const [cancelPopup, setCancelPopup] = useState(false);
  const [voluntaryClaims, setVoluntaryClaims] = useState([]);

  const slideToggleClass = config["outline_toggle"]
    ? "toggle-outline"
    : "toggle-no-outline";

  const hasAllElement = (mainArray, subArray) =>
    subArray.every((ele) => mainArray.includes(ele));

  const union = (mainArray, subArray) =>
    Array.from(new Set([...mainArray, ...subArray]));

  const difference = (mainArray, subArray) =>
    mainArray.filter((el) => !subArray.includes(el));

  useEffect(() => {
    if (voluntaryClaims.length === 0) {
      setVoluntaryClaims(
        openIDConnectService.getOAuthDetails().voluntaryClaims
      );
    }
    setVoluntaryClaims(
      openIDConnectService
        .getOAuthDetails()
        .voluntaryClaims.filter((item) => !claims.includes(item))
    );
  }, [claims, openIDConnectService]);

  const formatArray = (arr) => {
    if (arr.length === 0) return "";
    if (arr.length === 1) return arr[0];
    if (arr.length === 2) return arr.join(` ${t("and")} `);

    return arr.slice(0, -1).join(", ") + ` ${t("and")} ` + arr[arr.length - 1];
  };

  const translateClaims = (claims) => {
    return claims.map((claim) => t(claim));
  };

  // Format and translate the claims
  const formattedClaims = formatArray(translateClaims(voluntaryClaims));

  const handleScopeChange = (e) => {
    let id = e.target.id;

    let resultArray = [];
    if (e.target.checked) {
      //if checked (true), then add this id into checkedList
      resultArray = scope.filter((CheckedId) => CheckedId !== id);
      resultArray.push(id);
    } //if not checked (false), then remove this id from checkedList
    else {
      resultArray = scope.filter((CheckedId) => CheckedId !== id);
    }
    setScope(resultArray);
  };

  const handleClaimChange = (e) => {
    let id = e.target.id;

    let resultArray = [];
    if (e.target.checked) {
      //if checked (true), then add this id into checkedList
      resultArray = claims.filter((CheckedId) => CheckedId !== id);
      resultArray.push(id);
    } //if not checked (false), then remove this id from checkedList
    else {
      resultArray = claims.filter((CheckedId) => CheckedId !== id);
    }
    setClaims(resultArray);
  };

  const selectUnselectAllScopeClaim = (e, claimScope, main = false) => {
    if (main) {
      if (claimScope.type === "scope") {
        setScope(
          e.target.checked
            ? union(scope, claimScope?.values)
            : difference(scope, claimScope?.values)
        );
      } else {
        setClaims(
          e.target.checked
            ? union(claims, claimScope?.values)
            : difference(claims, claimScope?.values)
        );
      }
    } else {
      if (claimScope.type === "scope") {
        handleScopeChange(e);
      } else {
        handleClaimChange(e);
      }
    }
  };

  const elementChecked = (id, value) => {
    let el = document.getElementById(id);
    if (el) {
      el.checked = value;
    }
  };

  const scopeClaimChanges = () => {
    const ids = {
      check: {
        scope: [],
        claim: [],
      },
      uncheck: {
        scope: [],
        claim: [],
      },
    };
    claimsScopes.forEach((claimScope) => {
      if (!claimScope?.required) {
        const data = claimScope.type === "scope" ? scope : claims;
        const hasAll = hasAllElement(data, claimScope?.values);
        const diff = difference(claimScope?.values, data);
        const hasNot = diff.length === claimScope.values.length;
        ids.check[claimScope.type] = hasAll
          ? [...data, claimScope.label]
          : data;
        ids.uncheck[claimScope.type] = hasNot
          ? [...diff, claimScope.label]
          : diff;
      }
    });

    ids.check.scope.forEach((_) => elementChecked(_, true));
    ids.uncheck.scope.forEach((_) => elementChecked(_, false));
    ids.check.claim.forEach((_) => elementChecked(_, true));
    ids.uncheck.claim.forEach((_) => elementChecked(_, false));
  };

  i18next.on("languageChanged", () => {
    setClientMultiLang();
  });

  const setClientMultiLang = (localLangMap = null) => {
    localLangMap ??= langMap;
    let oAuthDetails = openIDConnectService.getOAuthDetails();
    let currentLanguage = localLangMap[i18next.language];
    let clientName =
      oAuthDetails.clientName[currentLanguage] ??
      oAuthDetails.clientName["@none"];
    setClientName(clientName);
  };

  //1. If consntAction= capture we will continue the flow in the useEffect and submit it
  //2. If consentAction = NoCapture we will directly submit it and return
  useEffect(() => {
    const initialize = async () => {
      if (consentAction === "NOCAPTURE") {
        submitConsent([], []);
        return;
      }

      const langConfig = await langConfigService.getLangCodeMapping();
      setLangMap(langConfig);

      let oAuthDetails = openIDConnectService.getOAuthDetails();

      let claimsScopes = [];
      claimsScopes.push({
        label: "authorize_scope",
        type: "scope",
        required: false,
        values: oAuthDetails?.authorizeScopes,
        tooltip: "authorize_scope_tooltip",
      });

      claimsScopes.push({
        label: "essential_claims",
        type: "claim",
        required: true,
        values: oAuthDetails?.essentialClaims,
        tooltip: "essential_claims_tooltip",
      });

      claimsScopes.push({
        label: "voluntary_claims",
        type: "claim",
        required: false,
        values: oAuthDetails?.voluntaryClaims,
        tooltip: "voluntary_claims_tooltip",
      });

      setClaimsScopes(claimsScopes);
      setClientMultiLang(langConfig);
      setClientLogoPath(oAuthDetails?.logoUrl);
      setClaims(oAuthDetails?.essentialClaims);
      setVoluntaryClaims(
        openIDConnectService.getOAuthDetails().voluntaryClaims
      );
    };
    if (firstRender.current) {
      firstRender.current = false;
      initialize();
      return;
    }
    scopeClaimChanges();
  }, [scope, claims]);

  useEffect(() => {
    if (isNaN(authTime)) {
      return;
    }
    const timer = setTimeout(() => {
      let currentTime = Math.floor(new Date().getTime() / 1000);
      let timePassed = currentTime - authTime;
      let tLeft = transactionTimeoutWithBuffer - timePassed;
      if (tLeft <= 0) {
        redirectOnError("transaction_timeout", t("transaction_timeout"));
        return;
      }
      setTimeLeft(tLeft);
    }, 1000);
    return () => {
      clearTimeout(timer);
    };
  }, [timeLeft]);

  useEffect(() => {
    if (isNaN(authTime)) {
      return;
    }
    let currentTime = Math.floor(new Date().getTime() / 1000);
    let timePassed = currentTime - authTime;
    let tLeft = transactionTimeoutWithBuffer - timePassed;
    setTimeLeft(tLeft);
  }, []);

  function formatTime(time) {
    const minutes = Math.floor(time / 60)
      .toString()
      .padStart(2, "0");
    const seconds = (time % 60).toString().padStart(2, "0");
    return `${minutes}:${seconds}`;
  }

  const handleSubmit = (e) => {
    e.preventDefault();
    submitConsent(claims, scope);
  };

  // open the modalpopup
  const handleCancel = (e) => {
    setCancelPopup(true);
    e.preventDefault();
  };

  //Handle Login API Integration here
  const submitConsent = async (acceptedClaims, permittedAuthorizeScopes) => {
    try {
      let transactionId = openIDConnectService.getTransactionId();

      setStatus(states.LOADING);

      window.onbeforeunload = null;

      const authCodeResponse = await post_AuthCode(
        transactionId,
        acceptedClaims,
        permittedAuthorizeScopes
      );

      const { response, errors } = authCodeResponse;

      if (errors != null && errors.length > 0) {
        redirectOnError(
          errors[0].errorCode,
          i18next.t("errors." + errors[0].errorCode)
        );
        return;
      }

      let params = "?";

      if (response.state) {
        params = params + "state=" + response.state + "&";
      }

      window.location.replace(
        response.redirectUri + params + "code=" + response.code
      );
    } catch (error) {
      redirectOnError("authorization_failed_msg", error.message);
    }
  };

  if (authTime === null) {
    redirectOnError("invalid_transaction", t("invalid_transaction"));
  }

  const sliderButtonDiv = (item, handleOnchange) => (
    <div>
      <label
        labelfor={item}
        className="inline-flex relative items-center mb-1 mt-1 cursor-pointer"
      >
        <input
          type="checkbox"
          value=""
          id={item}
          className="sr-only peer"
          onChange={handleOnchange}
        />
        <div
          className={
            "peer ltr:peer-checked:after:translate-x-full rtl:peer-checked:after:-translate-x-full border after:border after:h-4 after:w-4 rounded-full after:rounded-full after:transition-all after:content-[''] after:absolute slide-toggle-button " +
            slideToggleClass
          }
        ></div>
      </label>
    </div>
  );

  if (consentAction === "NOCAPTURE") {
    return (
      <div className="flex items-center justify-center section-background">
        <div className="max-w-md w-full shadow mt-5 rounded loading-indicator px-4 py-4">
        <LoadingIndicator size="medium" message="redirecting_msg" className="align-loading-center"/>
        </div>
      </div>
    );
  }

  // close the modalpopup
  const handleStay = () => {
    setCancelPopup(false);
  };

  // close the modalpopup and redirect to Relying Party landing page
  const handleDiscontinue = () => {
    redirectOnError("consent_request_rejected", t("consent_request_rejected"));
  };

  // buttons for the modalpopup footer
  var footerButtons = (
    <div className="mx-2 w-full md:mx-5">
      <div className="mb-2">
        <button
          type="button"
          className="flex justify-center w-full font-medium rounded-lg text-sm px-5 py-4 text-center border-2 primary-button"
          onClick={handleStay}
        >
          {t("cancelpopup.stay_btn")}
        </button>
      </div>
      <button
        type="button"
        className="flex justify-center w-full font-medium rounded-lg text-sm px-5 py-4 text-center border-2 secondary-button"
        onClick={handleDiscontinue}
      >
        {t("cancelpopup.discontinue_btn")}
      </button>
    </div>
  );

  return (
    authTime &&
    clientName &&
    claimsScopes.length > 0 && (
      <>
        {cancelPopup && (
          <ModalPopup
            alertIcon="images/warning_message_icon.svg"
            alertClassname="flex flex-shrink-0 items-center justify-center rounded-t-md p-4 mt-4"
            header={t("cancelpopup.confirm_header")}
            headerClassname="relative text-center text-dark font-semibold text-xl text-[#2B3840] mt-4"
            body={t("cancelpopup.confirm_text")}
            bodyClassname="relative px-4 py-3 text-center"
            footer={footerButtons}
            footerClassname="flex flex-shrink-0 flex-wrap items-center justify-center rounded-b-md p-4 mb-5 mt-3"
          />
        )}
        <div className="multipurpose-login-card shadow w-full md:w-3/6 md:z-10 lg:max-w-sm m-0 md:m-auto">
          <div className="bg-[#FFF9F0] rounded-t-lg top-0 left-0 right-0 p-2">
            {timeLeft && timeLeft > 0 && status !== LoadingStates.LOADING && (
              <div className="text-center">
                <p className="text-[#4E4E4E] font-semibold">
                  {t("transaction_timeout_msg")}
                </p>
                <p className="font-bold text-[#DE7A24]">
                  {formatTime(timeLeft)}{" "}
                </p>
              </div>
            )}
          </div>
          <div className="flex flex-col flex-grow lg:px-5 md:px-4 sm:px-3 px-3 pb-4">
            <div className="w-full flex mt-9 justify-center items-center">
              <img
                className="object-contain client-logo-size"
                src={clientLogoPath}
                alt={clientName}
              />
              <span className="flex mx-5 alternate-arrow"></span>
              <img
                className="object-contain brand-only-logo client-logo-size"
                alt={t("logo_alt")}
              />
            </div>
            <form className="space-y-4" onSubmit={handleSubmit}>
              <div className="flex justify-center mt-[30px]">
                <b>
                  {t("consent_request_msg", {
                    clientName: clientName,
                  })}
                </b>
              </div>
              {claimsScopes?.map(
                (claimScope) =>
                  claimScope?.values?.length > 0 && (
                    <div key={claimScope.label}>
                      <div className="grid sm:grid-cols-2 grid-cols-2 sm:gap-4">
                        <div className="flex sm:justify-start w-max">
                          <div className="font-semibold">
                            {t(claimScope.label)}
                            <button
                              id={claimScope.tooltip}
                              className="ml-1 text-sky-600 text-xl info-icon-button"
                              data-tooltip-content={t(claimScope.tooltip)}
                              data-tooltip-place="top"
                              onClick={(e) => {
                                e.preventDefault();
                              }}
                              role="tooltip"
                            >
                              &#9432;
                            </button>
                            <ReactTooltip
                              anchorId={claimScope.tooltip}
                              className="md:w-3/6 lg:max-w-sm m-0 md:m-auto"
                            />
                          </div>
                        </div>
                        <div className="flex justify-end mr-4 ml-4">
                          {!claimScope?.required &&
                            claimScope.values.length > 1 &&
                            sliderButtonDiv(claimScope.label, (e) =>
                              selectUnselectAllScopeClaim(e, claimScope, true)
                            )}
                        </div>
                      </div>

                      <div className="divide-y">
                        {claimScope?.values?.map((item) => (
                          <ul className="list-disc marker:text-[#B9B9B9] ml-4 mr-4">
                            <li key={item}>
                              <div className="grid grid-cols-2 gap-4">
                                <div className="flex justify-start relative items-center mb-1 mt-1">
                                  <label
                                    className={`text-sm ${
                                      claimScope?.label ===
                                        "voluntary_claims" &&
                                      voluntaryClaims.includes(item)
                                        ? "text-[#8D8D8DD5]"
                                        : "text-[#01070DD5]"
                                    }`}
                                  >
                                    {t(item)}
                                  </label>
                                </div>
                                <div className="flex justify-end">
                                  {claimScope?.required && (
                                    <label
                                      labelfor={item}
                                      className="inline-flex text-sm relative items-center mb-1 mt-1 text-gray-400"
                                    >
                                      {t("required")}
                                    </label>
                                  )}
                                  {!claimScope?.required &&
                                    sliderButtonDiv(item, (e) =>
                                      selectUnselectAllScopeClaim(e, claimScope)
                                    )}
                                </div>
                              </div>
                            </li>
                          </ul>
                        ))}
                      </div>
                    </div>
                  )
              )}
              {voluntaryClaims.length !== 0 && (
                <div className="no-claims-record-banner">
                  {t("noRecordClaimsMessage", { claims: formattedClaims })}
                </div>
              )}
              {
                <div>
                  {status === LoadingStates.LOADING && (
                    <LoadingIndicator size="medium" message="redirecting_msg" />
                  )}
                </div>
              }
              {status !== LoadingStates.LOADING && (
                <div className="grid gap-y-2">
                  <FormAction
                    type={buttonTypes.button}
                    text={t("allow")}
                    handleClick={handleSubmit}
                    id="continue"
                  />
                  <FormAction
                    type={buttonTypes.cancel}
                    text={t("cancel")}
                    handleClick={handleCancel}
                    id="cancel"
                  />
                </div>
              )}
            </form>
          </div>
        </div>
      </>
    )
  );
}
