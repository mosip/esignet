import React from "react";
import { useEffect, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import ErrorIndicator from "../common/ErrorIndicator";
import LoadingIndicator from "../common/LoadingIndicator";
import { LoadingStates as states } from "../constants/states";

export default function Authorize({
  authService,
}) {

  const get_CsrfToken = authService.get_CsrfToken;
  const post_OauthDetails = authService.post_OauthDetails;
  const buildRedirectParams = authService.buildRedirectParams;
  const storeQueryParam = authService.storeQueryParam;

  const [status, setStatus] = useState(states.LOADING);
  const [oAuthDetailResponse, setOAuthDetailResponse] = useState(null);
  const [error, setError] = useState(null);
  const [searchParams, setSearchParams] = useSearchParams();

  const navigate = useNavigate();

  useEffect(() => {
    const callAuthorize = async () => {
      try {
        storeQueryParam(searchParams.toString());

        let nonce = searchParams.get("nonce");
        let state = searchParams.get("state");

        let client_id = searchParams.get("client_id");
        let redirect_uri = searchParams.get("redirect_uri");
        let response_type = searchParams.get("response_type");
        let scope = searchParams.get("scope");
        let acr_values = searchParams.get("acr_values");
        let claims = searchParams.get("claims");
        let claimsLocales = searchParams.get("claims_locales");
        let display = searchParams.get("display");
        let maxAge = searchParams.get("max_age");
        let prompt = searchParams.get("prompt");
        let uiLocales = searchParams.get("ui_locales");
        let codeChallenge = searchParams.get("code_challenge");
        let codeChallengeMethod = searchParams.get("code_challenge_method");

        let claimsDecoded;
        if (claims == null) {
          claimsDecoded = null;
        } else {
          try {
            claimsDecoded = JSON.parse(decodeURI(claims));
          } catch {
            setError("parsing_error_msg");
            setStatus(states.ERROR);
            return;
          }
        }

        await get_CsrfToken();

        const response = await post_OauthDetails(
          nonce,
          state,
          client_id,
          redirect_uri,
          response_type,
          scope,
          acr_values,
          claimsDecoded,
          claimsLocales,
          display,
          maxAge,
          prompt,
          uiLocales,
          codeChallenge,
          codeChallengeMethod
        );

        setOAuthDetailResponse(response);
        setStatus(states.LOADED);
      } catch (error) {
        setOAuthDetailResponse(null);
        setError(error.message);
        setStatus(states.ERROR);
      }
    };

    callAuthorize();
  }, []);

  useEffect(() => {
    if (status === states.LOADED) {
      redirectToLogin();
    }
  }, [status]);

  const redirectToLogin = async () => {
    if (!oAuthDetailResponse) {
      return;
    }

    const { response, errors } = oAuthDetailResponse;

    if (!response) {
      return;
    }

    if (errors != null && errors.length > 0) {
      return;
    } else {
      try {
        let nonce = searchParams.get("nonce");
        let state = searchParams.get("state");
        let params = buildRedirectParams(nonce, state, response);

        navigate(process.env.PUBLIC_URL + "/login" + params, {
          replace: true,
        });
      } catch (error) {
        setOAuthDetailResponse(null);
        setError("Failed to load");
        setStatus(states.ERROR);
      }
    }
  };

  let el;

  switch (status) {
    case states.LOADING:
      el = <LoadingIndicator size="medium" message={"loading_msg"} />;
      break;
    case states.LOADED:
      if (!oAuthDetailResponse) {
        el = (
          <ErrorIndicator
            errorCode="no_response_msg"
            defaultMsg="No response"
          />
        );
        break;
      }

      const { errors } = oAuthDetailResponse;

      if (errors != null && errors.length > 0) {
        el = errors?.map(({ errorCode, errorMessage }, idx) => (
          <div key={idx}>
            <ErrorIndicator errorCode={errorCode} defaultMsg={errorMessage} />
          </div>
        ));
      }
      break;
    case states.ERROR:
      el = <ErrorIndicator errorCode={error} defaultMsg={error} />;
      break;
  }

  return el;
}
