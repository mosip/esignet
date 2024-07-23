import React, { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import openIDConnectService from "../services/openIDConnectService";
import authService from "../services/authService";
import PopoverContainer from "../common/Popover";
import ModalPopup from "../common/ModalPopup";
import redirectOnError from "../helpers/redirectOnError";
import { configurationKeys } from "../constants/clientConstants";
import LoadingIndicator from "../common/LoadingIndicator";

const ClaimDetails = ({
  i18nKeyPrefix1 = "consentDetails",
  i18nKeyPrefix2 = "errors",
}) => {
  const { t: t1, i18n } = useTranslation("translation", {
    keyPrefix: i18nKeyPrefix1,
  });
  const { t: t2 } = useTranslation("translation", {
    keyPrefix: i18nKeyPrefix2,
  });
  const [claimsScopes, setClaimsScopes] = useState([]);
  const [isPopup, setPopup] = useState(false);
  const [isProceedDisabled, setProceedDisabled] = useState(false);
  const [isLoading, setIsLoading] = useState(false);

  // Parsing the current URL into a URL object
  const urlObj = new URL(window.location.href);

  // Extracting the value of the 'state' and 'nonce' parameter from the URL
  const state = urlObj.searchParams.get("state");
  const nonce = urlObj.searchParams.get("nonce");

  // Extracting the hash part of the URL (excluding the # character)
  const code = urlObj.hash.substring(1);

  // Decoding the Base64-encoded string (excluding the first character)
  const decodedBase64 = atob(code);

  // Creating an instance of the openIDConnectService with decodedBase64, nonce, and state parameters
  const oidcService = new openIDConnectService(
    JSON.parse(decodedBase64),
    nonce,
    state
  );

  const authServices = new authService(oidcService);

  const oAuth_Details = oidcService.getOAuthDetails();
  const transactionId = oidcService.getTransactionId();

  const eKYCStepsURL = oidcService.getEsignetConfiguration(
    configurationKeys.eKYCStepsConfig
  );

  const mergeArrays = (arr1, arr2) => {
    return arr2
      .map((item) => {
        const match = arr1.find((item1) => item1.claim === item);
        return match
          ? {
              claim: item,
              available: match.available,
              verified: match.verified,
            }
          : null;
      })
      .filter(Boolean)
      .sort((a, b) => (a.available === b.available ? 0 : a.available ? -1 : 1));
  };

  const createClaimsTooltip = (label) => {
    if (label === "essential") {
      return (
        <div>
          <p className="mb-1">
            <span className="!font-semibold">{t1("essential_claims")}: </span>
            {t1("essentialClaimsTooltip")}
          </p>
          <p className="mb-1">
            <span className="!font-semibold">{t1("verified_claims")}: </span>
            {t1("verifiedClaimTooltip")}
          </p>
          <p className="mb-1">
            <span className="!font-semibold">{t1("unverified_claims")}: </span>
            {t1("unverifiedClaimTooltip")}
          </p>
        </div>
      );
    } else if (label === "voluntary") {
      return (
        <div>
          <p className="mb-1">{t1("voluntaryClaimsTooltip")}</p>
        </div>
      );
    }
  };

  const getAllClaimDetails = async () => {
    try {
      const { response, errors } = await authServices.getClaimDetails();
      if (errors?.length) {
        redirectOnError(errors[0].errorCode, t2(errors[0].errorCode));
        return;
      }

      const claimsScopes = [
        {
          label: "essential_claims",
          type: "claim",
          required: true,
          values: mergeArrays(
            response?.claimStatus,
            oAuth_Details.essentialClaims
          ),
          tooltip: createClaimsTooltip("essential"),
        },
        {
          label: "voluntary_claims",
          type: "claim",
          required: false,
          values: mergeArrays(
            response?.claimStatus,
            oAuth_Details.voluntaryClaims
          ),
          tooltip: createClaimsTooltip("voluntary"),
        },
      ];
      if (!response?.profileUpdateRequired) {
        window.onbeforeunload = null;
        if (response?.consentAction === "CAPTURE") {
          window.location.replace(
            window.location.href.replace("claim-details", "consent")
          );
        } else if (response?.consentAction === "NOCAPTURE") {
          const { response: authCodeResponse, errors: authCodeErrors } =
            await authServices.post_AuthCode(transactionId, [], []);
          if (authCodeErrors?.length) {
            redirectOnError(
              authCodeErrors[0].errorCode,
              t2(authCodeErrors[0].errorCode)
            );
          } else {
            window.location.replace(
              `${authCodeResponse.redirectUri}?state=${authCodeResponse.state}&code=${authCodeResponse.code}`
            );
          }
        }
      } else {
        setClaimsScopes(claimsScopes);
      }
    } catch (error) {
      redirectOnError("authorization_failed_msg", error.message);
    }
  };

  useEffect(() => {
    getAllClaimDetails();
  }, []);

  const handleProceed = async () => {
    setProceedDisabled(true);
    window.onbeforeunload = null;
    try {
      function randomKey(prefix) {
        const timestamp = new Date().getTime();
        return `${prefix}${timestamp}`;
      }

      const key = randomKey("urlInfo");

      for (let key in localStorage) {
        if (key.startsWith("urlInfo")) {
          localStorage.removeItem(key);
        }
      }

      localStorage.setItem(
        key,
        window.location.search.substring(1) +
          "#" +
          window.location.hash.substring(1)
      );

      const { response, errors } = await authServices.prepareSignupRedirect(
        transactionId,
        key
      );
      if (errors?.length) {
        redirectOnError(errors[0].errorCode, t2(errors[0].errorCode));
      } else {
        const encodedIdToken = btoa(
          `id_token_hint=${response.idToken}&ui_locales=${i18n.language}`
        );
        window.location.replace(
          `${eKYCStepsURL}?state=${state}#${encodedIdToken}`
        );
      }
    } catch (error) {
      redirectOnError("authorization_failed_msg", error.message);
    } finally {
      setIsLoading(true);
      setProceedDisabled(false);
    }
  };

  const handleCancel = () => {
    setPopup(true);
  };

  const handleStay = () => {
    setPopup(false);
  };

  // close the modalpopup and redirect to Relying Party landing page
  const handleDiscontinue = () => {
    redirectOnError("consent_details_rejected", t1("consent_details_rejected"));
  };

  // buttons for the modalpopup footer
  var footerButtons = (
    <div className="mx-2 w-full md:mx-5">
      <div className="mb-2">
        <button
          id="stay-button"
          type="button"
          className="flex justify-center w-full font-medium rounded-lg text-sm px-5 py-4 text-center border-2 primary-button"
          onClick={handleStay}
        >
          {t1("stay_btn")}
        </button>
      </div>
      <button
        id="discontinue-button"
        type="button"
        className="flex justify-center w-full font-medium rounded-lg text-sm px-5 py-4 text-center border-2 secondary-button"
        onClick={handleDiscontinue}
      >
        {t1("discontinue_btn")}
      </button>
    </div>
  );

  return isPopup ? (
    <ModalPopup
      header={t1("popup_header")}
      headerClassname="relative text-center text-dark font-semibold text-xl text-[#2B3840] mt-9"
      body={t1("popup_body", {
        clientName: oAuth_Details?.clientName["@none"],
      })}
      bodyClassname="relative pt-3 text-center mx-5"
      footer={footerButtons}
      footerClassname="flex flex-shrink-0 flex-wrap items-center justify-center rounded-b-md p-4 my-4"
    />
  ) : claimsScopes.length === 0 || isLoading ? (
    <LoadingIndicator
      size="medium"
      message={"loading_msg"}
      className="align-loading-center"
    />
  ) : (
    <>
      <img
        className="top_left_bg_logo hidden md:block"
        alt="top left background"
      />
      <img
        className="bottom_left_bg_logo hidden md:block"
        alt="bottom left background"
      />
      <div
        className="relative z-50 consent-details"
        aria-labelledby="modal-title"
        role="dialog"
        aria-modal="true"
      >
        <div className="fixed inset-0 bg-gray-500 bg-opacity-75 transition-opacity"></div>
        <div className="fixed inset-0 z-10 w-screen overflow-y-auto">
          <div className="flex min-h-full items-end justify-center p-4 text-center sm:items-center sm:p-0">
            <div className="relative transform overflow-hidden rounded-[20px] bg-white text-left shadow-xl transition-all duration-300 ease-out sm:my-8 sm:w-full sm:max-w-[28rem] w-screen m-auto">
              <div className="flex flex-shrink-0 flex-wrap items-center justify-center rounded-b-md p-4 py-0 my-5">
                <div className="header my-2">{t1("header")}</div>
                <div className="w-full flex my-3 justify-center items-center">
                  <img
                    className="client-logo-size"
                    src={oAuth_Details?.logoUrl}
                    alt={oAuth_Details?.clientName}
                  />
                  <img
                    className="h-5 mx-5"
                    src="/images/sync_alt_black.svg"
                    alt="sync_alt"
                  />
                  <img
                    className="brand-only-logo client-logo-size"
                    alt={t1("logo_alt")}
                  />
                </div>
                <p className="sub-header m-0 mt-1 md:mx-5 md:mb-1 md:mt-3">
                  {t1("sub-header", {
                    clientName: oAuth_Details?.clientName["@none"],
                  })}
                </p>
                <div className="claims-list mx-0 md:mx-5">
                  {claimsScopes?.map(
                    (claimScope) =>
                      claimScope?.values?.length > 0 && (
                        <div key={claimScope.label} className="mt-2">
                          <div className="grid sm:grid-cols-2 grid-cols-2 sm:gap-4">
                            <div className="flex sm:justify-start w-max">
                              <div className="font-semibold mb-1">
                                {t1(claimScope.label)}
                                <PopoverContainer
                                  child={
                                    <svg
                                      xmlns="http://www.w3.org/2000/svg"
                                      width="16"
                                      height="16"
                                      viewBox="0 0 18.5 18.5"
                                      className="mx-1 mt-[2px] w-[15px] h-[14px] inline relative bottom-[2px]"
                                    >
                                      <g
                                        id="info_FILL0_wght400_GRAD0_opsz48"
                                        transform="translate(0.25 0.25)"
                                      >
                                        <path
                                          id="info_FILL0_wght400_GRAD0_opsz48-2"
                                          data-name="info_FILL0_wght400_GRAD0_opsz48"
                                          d="M88.393-866.5h1.35v-5.4h-1.35ZM89-873.565a.731.731,0,0,0,.529-.207.685.685,0,0,0,.214-.513.752.752,0,0,0-.213-.545.707.707,0,0,0-.529-.22.708.708,0,0,0-.529.22.751.751,0,0,0-.214.545.686.686,0,0,0,.213.513A.729.729,0,0,0,89-873.565ZM89.006-862a8.712,8.712,0,0,1-3.5-.709,9.145,9.145,0,0,1-2.863-1.935,9.14,9.14,0,0,1-1.935-2.865,8.728,8.728,0,0,1-.709-3.5,8.728,8.728,0,0,1,.709-3.5,9,9,0,0,1,1.935-2.854,9.237,9.237,0,0,1,2.865-1.924,8.728,8.728,0,0,1,3.5-.709,8.728,8.728,0,0,1,3.5.709,9.1,9.1,0,0,1,2.854,1.924,9.089,9.089,0,0,1,1.924,2.858,8.749,8.749,0,0,1,.709,3.5,8.712,8.712,0,0,1-.709,3.5,9.192,9.192,0,0,1-1.924,2.859,9.087,9.087,0,0,1-2.857,1.935A8.707,8.707,0,0,1,89.006-862Zm.005-1.35a7.348,7.348,0,0,0,5.411-2.239,7.4,7.4,0,0,0,2.228-5.422,7.374,7.374,0,0,0-2.223-5.411A7.376,7.376,0,0,0,89-878.65a7.4,7.4,0,0,0-5.411,2.223A7.357,7.357,0,0,0,81.35-871a7.372,7.372,0,0,0,2.239,5.411A7.385,7.385,0,0,0,89.011-863.35ZM89-871Z"
                                          transform="translate(-80 880)"
                                          strokeWidth="0.75"
                                        />
                                      </g>
                                    </svg>
                                  }
                                  content={claimScope.tooltip}
                                  position="bottom"
                                  contentSize="text-xs"
                                  contentClassName="rounded-md px-3 py-2 border border-[#BCBCBC] outline-0 bg-white shadow-md z-50 w-screen sm:w-[26rem] leading-none"
                                />
                              </div>
                            </div>
                          </div>

                          <div className="divide-y">
                            {claimScope?.values?.map((item) => (
                              <ul className="list-disc marker:text-[#B9B9B9] ml-4 !border-0">
                                <li key={item} className="mb-1">
                                  <div className="claimsGrid">
                                    <div className="flex justify-start relative items-center mb-1 mt-1">
                                      <label
                                        className={`${
                                          item.available
                                            ? "text-[#B9B9B9] inline-block"
                                            : null
                                        }`}
                                      >
                                        <span className="mr-1">
                                          {t1(item.claim)}
                                        </span>
                                        {item.verified ? (
                                          <em className="text-[#B9B9B9] inline-block">
                                            ({t1("verified")})
                                          </em>
                                        ) : (
                                          <em className="text-[#B9B9B9] inline-block">
                                            ({t1("not-verified")})
                                          </em>
                                        )}
                                      </label>
                                    </div>
                                    <div className="flex justify-end">
                                      {item.available ? (
                                        <span className="available-claim">
                                          {t1("available")}
                                        </span>
                                      ) : (
                                        <span className="not-available-claim">
                                          {t1("not-available")}
                                        </span>
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
                </div>
                <div className="message mx-0 px-2 mt-2 md:mx-5">
                  {t1("message")}
                </div>
                <div className="mx-0 w-full mt-2 md:mx-5">
                  <div className="mb-3">
                    <button
                      id="proceed-button"
                      type="button"
                      className="flex justify-center w-full font-medium rounded-lg text-sm px-5 py-4 text-center border-2 primary-button"
                      onClick={handleProceed}
                      disabled={isProceedDisabled}
                    >
                      {t1("proceed")}
                    </button>
                  </div>
                  <div className="mt-3">
                    <button
                      id="cancel-button"
                      type="button"
                      className="flex justify-center w-full font-medium rounded-lg text-sm px-5 py-4 text-center border-2 secondary-button"
                      onClick={handleCancel}
                      disabled={isProceedDisabled}
                    >
                      {t1("cancel")}
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </>
  );
};

export default ClaimDetails;
