import React, { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { LoadingStates as states } from "../constants/states";
import LoadingIndicator from "../common/LoadingIndicator";
import ErrorIndicator from "../common/ErrorIndicator";
import { Buffer } from "buffer";

export default function ProxyAuthorization({ relyingPartyService }) {
  const { post_proxyAuthCode } = {
    ...relyingPartyService,
  };

  const [searchParams, setSearchParams] = useSearchParams();
  const [error, setError] = useState({ errorCode: "", errorMsg: "" });
  const [loadingMsg, setLoadingMsg] = useState("");
  const [status, setStatus] = useState(states.LOADING);

  const onError = (errorCode, errorDescription, redirect_uri) => {
    let params = "?";
    if (errorDescription) {
      params = params + "error_description=" + errorDescription + "&";
    }

    //REQUIRED
    params = params + "error=" + errorCode;

    window.location.replace(redirect_uri + params);
  };

  useEffect(() => {
    const getSearchParams = async () => {
      setLoadingMsg("authenticating_msg");

      const authCode = searchParams.get("code");
      const errorCode = searchParams.get("error");
      const error_desc = searchParams.get("error_description");
      const encodedState = searchParams.get("state") ?? "";

      const { transactionId, redirect_uri } = decodeState(encodedState);

      if (!transactionId || !redirect_uri) {
        setError({
          errorCode: "invalid_state_response",
        });
        setStatus(states.ERROR);
        return;
      }

      if (errorCode) {
        onError(errorCode, error_desc, redirect_uri);
        return;
      }

      if (authCode) {
        fetchProxyAuthCode(transactionId, authCode, redirect_uri);
      } else {
        setError({
          errorCode: "authCode_missing",
        });
        setStatus(states.ERROR);
        return;
      }
    };
    getSearchParams();
  }, []);

  const decodeState = (encodedState) => {
    var decodedState = Buffer.from(encodedState, "base64")?.toString();
    var stateDetails = decodedState.split(" ");

    if (stateDetails && stateDetails.length >= 2) {
      var transactionId = stateDetails[0];
      var redirect_uri = stateDetails[1];
      return { transactionId, redirect_uri };
    }
    return {};
  };

  const fetchProxyAuthCode = async (transactionId, authCode, redirect_uri) => {
    try {
      var proxyAuthCodeResponse = await post_proxyAuthCode(
        authCode,
        transactionId
      );

      const { response, errors } = proxyAuthCodeResponse;

      if (errors != null && errors.length > 0) {
        onError(errors[0].errorCode, errors[0].errorMessage, redirect_uri);
        return;
      }

      setLoadingMsg("redirecting_msg");

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
    } catch (errormsg) {
      setError({ errorCode: "", errorMsg: errormsg.message });
      setStatus(states.ERROR);
    }
  };

  let el = (
    <div className="h-5/6 flex items-center justify-center py-12 px-4 sm:px-6 lg:px-8">
      <div className="max-w-md w-full space-y-8">
        {status === states.LOADING && (
          <LoadingIndicator size="medium" message={loadingMsg} />
        )}
        {status === states.ERROR && (
          <ErrorIndicator
            errorCode={error.errorCode}
            errorMsg={error.errorMsg}
          />
        )}
      </div>
    </div>
  );

  return el;
}
