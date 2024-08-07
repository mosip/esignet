import React from "react";
import { useEffect, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import ErrorIndicator from "../common/ErrorIndicator";
import LoadingIndicator from "../common/LoadingIndicator";
import { LoadingStates as states } from "../constants/states";
import localStorageService from "../services/local-storageService";
import sha256 from "crypto-js/sha256";
import Base64 from "crypto-js/enc-base64";

const { getCookie } = { ...localStorageService };

export default function Authorize({ authService }) {
  const get_CsrfToken = authService.get_CsrfToken;
  const post_OauthDetails_v2 = authService.post_OauthDetails_v2;
  const post_OauthDetails_v3 = authService.post_OauthDetails_v3;
  const buildRedirectParams = authService.buildRedirectParams;
  const storeQueryParam = authService.storeQueryParam;
  const post_AuthenticateUser = authService.post_AuthenticateUser;
  const post_AuthCode = authService.post_AuthCode;

  const [status, setStatus] = useState(states.LOADING);
  const [oAuthDetailResponse, setOAuthDetailResponse] = useState(null);
  const [error, setError] = useState(null);
  const [searchParams, setSearchParams] = useSearchParams();

  const navigate = useNavigate();

  const base64UrlDecode = (str) => {
    return decodeURIComponent(
      atob(str.replace(/-/g, "+").replace(/_/g, "/"))
        .split("")
        .map((c) => `%${("00" + c.charCodeAt(0).toString(16)).slice(-2)}`)
        .join("")
    );
  };

  const getDataFromCookie = (idTokenHint) => {
    const uuid = JSON.parse(base64UrlDecode(idTokenHint.split(".")[1])).sub;

    const code = "code";

    return { uuid, code };
  };

  const getOauthDetailsHash = async (value) => {
    let sha256Hash = sha256(JSON.stringify(value));
    let hashB64 = Base64.stringify(sha256Hash)
      .replace(/=+$/, "")
      .replace(/\+/g, "-")
      .replace(/\//g, "_");
    return hashB64;
  };

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

        if (!request.idTokenHint) {
          await get_CsrfToken();
          storeQueryParam(searchParams.toString());
        }

        const filteredRequest = Object.fromEntries(
          Object.entries({ ...request, claims: claimsDecoded }).filter(
            ([_, value]) => value !== null
          )
        );

        const handleResponse = async (oAuthDetailsResponse) => {
          if (oAuthDetailsResponse.errors.length === 0) {
            setOAuthDetailResponse(oAuthDetailsResponse);

            if (request.idTokenHint) {
              const { uuid, code } = getDataFromCookie(request.idTokenHint);

              if (code) {
                const { transactionId, authFactors } =
                  oAuthDetailsResponse.response;
                const challenge = { token: request.idTokenHint, code };
                const encodedChallenge = btoa(JSON.stringify(challenge));
                const challengeList = [
                  {
                    format: "base64url-encoded-json",
                    challenge: encodedChallenge,
                    authFactorType: authFactors[0][0].type,
                  },
                ];

                const hash = await getOauthDetailsHash(
                  oAuthDetailsResponse.response
                );

                const authenticateResponse = await post_AuthenticateUser(
                  transactionId,
                  uuid,
                  challengeList,
                  null,
                  hash
                );
                if (authenticateResponse.errors.length === 0) {
                  const authCodeResponse = await post_AuthCode(
                    transactionId,
                    [],
                    [],
                    hash
                  );
                  if (authCodeResponse.errors.length === 0) {
                    window.onbeforeunload = null;
                    const paramObj = {
                      state: authCodeResponse.response.state,
                      code: authCodeResponse.response.code,
                      ui_locales: request.uiLocales,
                    };

                    const redirectParams = new URLSearchParams(
                      paramObj
                    ).toString();

                    const encodedValue = btoa(redirectParams);
                    window.location.replace(
                      `${authCodeResponse.response.redirectUri}#${encodedValue}`
                    );
                  }
                }
              }
            } else {
              setStatus(states.LOADED);
            }
          }
        };

        if (!request.idTokenHint) {
          await post_OauthDetails_v2(filteredRequest).then(handleResponse);
        } else {
          await post_OauthDetails_v3(filteredRequest).then(handleResponse);
        }
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
