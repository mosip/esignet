import React from "react";
import { useEffect, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import ErrorIndicator from "../common/ErrorIndicator";
import LoadingIndicator from "../common/LoadingIndicator";
import { LoadingStates as states } from "../constants/states";
import { decodeHash, getOauthDetailsHash } from "../helpers/utils";

export default function Authorize({ authService }) {
  const get_CsrfToken = authService.get_CsrfToken;
  const post_OauthDetails_v3 = authService.post_OauthDetails_v3;
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
        setStatus(states.LOADING);

        const extractParam = (param) => searchParams.get(param);

        const request = {
          nonce: extractParam("nonce"),
          state: extractParam("state"),
          clientId: extractParam("client_id"),
          redirectUri: extractParam("redirect_uri"),
          responseType: extractParam("response_type"),
          scope: extractParam("scope"),
          acrValues: extractParam("acr_values"),
          claims: extractParam("claims"),
          claimsLocales: extractParam("claims_locales"),
          display: extractParam("display"),
          maxAge: extractParam("max_age"),
          prompt: extractParam("prompt"),
          uiLocales: extractParam("ui_locales"),
          codeChallenge: extractParam("code_challenge"),
          codeChallengeMethod: extractParam("code_challenge_method"),
          idTokenHint: extractParam("id_token_hint"),
        };

        let claimsDecoded = null;
        if (request.claims) {
          try {
            claimsDecoded = JSON.parse(decodeURI(request.claims));
          } catch {
            setError("parsing_error_msg");
            setStatus(states.ERROR);
            return;
          }
        }

        await get_CsrfToken();
        storeQueryParam(searchParams.toString());

        const filteredRequest = Object.fromEntries(
          Object.entries({ ...request, claims: claimsDecoded }).filter(
            ([_, value]) => value !== null
          )
        );

        const handleResponse = async (oAuthDetailsResponse) => {
          setStatus(states.LOADED);
          if (oAuthDetailsResponse.errors.length === 0) {
            setOAuthDetailResponse(oAuthDetailsResponse);
          } else {
            setOAuthDetailResponse(null);
            setError(oAuthDetailsResponse.errors[0].errorCode);
            setStatus(states.ERROR);
          }
        };

        await post_OauthDetails_v3(filteredRequest).then(handleResponse);
      } catch (error) {
        setStatus(states.LOADED);
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
      el = (
        <LoadingIndicator
          size="medium"
          message={"loading_msg"}
          className="align-loading-center"
        />
      );
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
