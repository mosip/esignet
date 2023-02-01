import i18next from "i18next";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import LoadingIndicator from "../common/LoadingIndicator";
import { buttonTypes } from "../constants/clientConstants";
import { LoadingStates, LoadingStates as states } from "../constants/states";
import FormAction from "./FormAction";

export default function Consent({
  authService,
  openIDConnectService,
  logoPath = "logo.png",
  i18nKeyPrefix = "consent",
}) {
  const { t } = useTranslation("translation", { keyPrefix: i18nKeyPrefix });

  const post_AuthCode = authService.post_AuthCode;

  const [status, setStatus] = useState(states.LOADED);
  const [claims, setClaims] = useState([]);
  const [scope, setScope] = useState([]);
  const [clientName, setClientName] = useState("");
  const [clientLogoPath, setClientLogoPath] = useState("");
  const [claimsScopes, setClaimsScopes] = useState([]);

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

  useEffect(() => {
    const initialize = async () => {
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
      setClientName(oAuthDetails?.clientName);
      setClientLogoPath(oAuthDetails?.logoUrl);

      setClaims(oAuthDetails?.essentialClaims);
    };
    initialize();
  }, []);

  const handleSubmit = (e) => {
    e.preventDefault();
    submitConsent();
  };

  const handleCancel = (e) => {
    e.preventDefault();
    onError("consent_request_rejected", t("consent_request_rejected"));
  };

  //Handle Login API Integration here
  const submitConsent = async () => {
    try {
      let transactionId = openIDConnectService.getTransactionId();
      let acceptedClaims = claims;
      let permittedAuthorizeScopes = scope;

      setStatus(states.LOADING);

      const authCodeResponse = await post_AuthCode(
        transactionId,
        acceptedClaims,
        permittedAuthorizeScopes
      );

      const { response, errors } = authCodeResponse;

      if (errors != null && errors.length > 0) {
        onError(
          errors[0].errorCode,
          i18next.t("errors." + errors[0].errorCode)
        );
        return;
      }

      let params = "?";
      if (response.nonce) {
        params = params + "nonce=" + response.nonce + "&";
      }

      if (response.state) {
        params = params + "state=" + response.state + "&";
      }

      window.location.replace(
        response.redirectUri + params + "code=" + response.code
      );
    } catch (error) {
      onError("authorization_failed_msg", error.message);
    }
  };

  //errorCode is REQUIRED, errorDescription is OPTIONAL
  const onError = async (errorCode, errorDescription) => {
    let nonce = openIDConnectService.getNonce();
    let state = openIDConnectService.getState();
    let redirect_uri = openIDConnectService.getRedirectUri();

    if (!redirect_uri) {
      return;
    }

    let params = "?";
    if (nonce) {
      params = params + "nonce=" + nonce + "&";
    }

    if (errorDescription) {
      params = params + "error_description=" + errorDescription + "&";
    }

    //REQUIRED
    params = params + "state=" + state + "&";

    //REQUIRED
    params = params + "error=" + errorCode;

    window.location.replace(redirect_uri + params);
  };

  return (
    <div className="flex items-center justify-center">
      <div className="max-w-md w-full shadow-lg mt-5 rounded bg-[#F8F8F8] px-4 py-4">
        <form className="space-y-4" onSubmit={handleSubmit}>
          <div className="flex justify-center items-center">
            <img className="h-20" src={clientLogoPath} alt={clientName} />
            <span className="text-6xl flex mx-5">&#8651;</span>
            <img className="h-20" src={logoPath} alt={t("logo_alt")} />
          </div>
          <div className="flex justify-center">
            <b>
              {t("consent_request_msg", {
                clientName: clientName,
              })}
            </b>
          </div>
          {claimsScopes?.map(
            (claimScope) =>
              claimScope?.values?.length > 0 && (
                <>
                  <h2 className="font-semibold" title={t(claimScope.tooltip)}>
                    {t(claimScope.label)}
                    <button className="ml-1 text-sky-600 text-xl"
                      onClick={(e) => { e.preventDefault(); }}
                    >
                      &#9432;
                    </button>
                  </h2>
                  <div className="divide-y">
                    {claimScope?.values?.map((item) => (
                      <div key={item}>
                        <div className="grid grid-cols-2 gap-4">
                          <div className="flex justify-start relative items-center mb-1 mt-1">
                            <label className="ml-3 text-sm text-black-900">
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
                            {!claimScope?.required && (
                              <label
                                labelfor={item}
                                className="inline-flex relative items-center mb-1 mt-1 cursor-pointer"
                              >
                                <input
                                  type="checkbox"
                                  value=""
                                  id={item}
                                  className="sr-only peer"
                                  onChange={
                                    claimScope.type === "scope"
                                      ? handleScopeChange
                                      : handleClaimChange
                                  }
                                />
                                <div className="w-9 h-5 border border-neutral-400 bg-white rounded-full peer after:content-[''] 
                                after:absolute after:top-[2px] after:bg-neutral-400 after:border after:border-neutral-400 
                                peer-checked:after:border-sky-500 after:rounded-full after:h-4 after:w-4 after:transition-all 
                                peer-checked:after:bg-sky-500 peer-checked:after:bg-sky-500 peer-checked:border-sky-500 
                                ltr:peer-checked:after:translate-x-full ltr:after:left-[2px] 
                                rtl:peer-checked:after:-translate-x-full rtl:after:right-[2px]"></div>
                              </label>
                            )}
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                </>
              )
          )}
          {
            <div>
              {status === LoadingStates.LOADING && (
                <LoadingIndicator size="medium" message="redirecting_msg" />
              )}
            </div>
          }
          {status !== LoadingStates.LOADING && (
            <div className="grid grid-cols-2 gap-4">
              <FormAction
                type={buttonTypes.cancel}
                text={t("cancel")}
                handleClick={handleCancel}
              />
              <FormAction
                type={buttonTypes.button}
                text={t("allow")}
                handleClick={handleSubmit}
              />
            </div>
          )}
        </form>
      </div>
    </div>
  );
}
