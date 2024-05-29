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
        let idTokenHint = atob(searchParams.get("id_token_hint"));

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

        if (idTokenHint === null || idTokenHint === undefined) {
          await get_CsrfToken();
        }

        let request = {
          nonce: nonce,
          state: state,
          clientId: client_id,
          redirectUri: redirect_uri,
          responseType: response_type,
          scope: scope,
          acrValues: acr_values,
          claims: claimsDecoded,
          claimsLocales: claimsLocales,
          display: display,
          maxAge: maxAge,
          prompt: prompt,
          uiLocales: uiLocales,
          codeChallenge: codeChallenge,
          codeChallengeMethod: codeChallengeMethod,
          idTokenHint: idTokenHint,
        };

        let filteredRequest = Object.fromEntries(
          Object.entries(request).filter(([key, value]) => value !== null)
        );

        if (idTokenHint === null || idTokenHint === undefined) {
          await post_OauthDetails_v2(filteredRequest).then(
            (oAuthDetailsResponse) => {
              if (oAuthDetailsResponse.errors.length === 0) {
                setOAuthDetailResponse(oAuthDetailsResponse);
                setStatus(states.LOADED);
              }
            }
          );
        } else {
          await post_OauthDetails_v3(filteredRequest).then(
            (oAuthDetailsResponse) => {
              if (oAuthDetailsResponse.errors.length === 0) {
                setOAuthDetailResponse(oAuthDetailsResponse);
                setStatus(states.LOADED);

                if (idTokenHint !== null && idTokenHint !== undefined) {
                  function base64UrlDecode(str) {
                    return decodeURIComponent(
                      atob(str.replace(/-/g, "+").replace(/_/g, "/"))
                        .split("")
                        .map(function (c) {
                          return (
                            "%" +
                            ("00" + c.charCodeAt(0).toString(16)).slice(-2)
                          );
                        })
                        .join("")
                    );
                  }

                  var uuid = JSON.parse(
                    base64UrlDecode(idTokenHint.split(".")[1])
                  ).sub;

                  var code = JSON.parse(
                    base64UrlDecode(getCookie(uuid).split(".")[0])
                  ).code;

                  if (code !== null && code !== undefined) {
                    let transactionId =
                      oAuthDetailsResponse.response.transactionId;

                    let challenge = {
                      token: idTokenHint,
                      code: code,
                    };

                    const encodedChallenge = btoa(JSON.stringify(challenge));

                    let challengeList = [
                      {
                        format: "base64url-encoded-json",
                        challenge: encodedChallenge,
                        authFactorType:
                          oAuthDetailsResponse.response.authFactors[0][0].type,
                      },
                    ];

                    const getOauthDetailsHash = async (value) => {
                      let sha256Hash = sha256(JSON.stringify(value));
                      let hashB64 = Base64.stringify(sha256Hash);
                      // Remove padding characters
                      hashB64 = hashB64.replace(/=+$/, "");
                      // Replace '+' with '-' and '/' with '_' to convert to base64 URL encoding
                      hashB64 = hashB64.replace(/\+/g, "-").replace(/\//g, "_");
                      return hashB64;
                    };

                    (async () => {
                      await post_AuthenticateUser(
                        transactionId,
                        uuid,
                        challengeList,
                        getOauthDetailsHash(oAuthDetailsResponse.response)
                      ).then((authenticateResponse) => {
                        if (authenticateResponse.errors.length === 0) {
                          (async () => {
                            await post_AuthCode(
                              transactionId,
                              [],
                              [],
                              getOauthDetailsHash(oAuthDetailsResponse.response)
                            ).then((authCodeResponse) => {
                              if (authCodeResponse.errors.length === 0) {
                                window.onbeforeunload = null;
                                const encodedStateCode = btoa(
                                  `state=${authCodeResponse.response.state}&code=${authCodeResponse.response.code}`
                                );
                                window.location.replace(
                                  `${authCodeResponse.response.redirectUri}#${encodedStateCode}`
                                );
                              }
                            });
                          })();
                        }
                      });
                    })();
                  }
                }
              }
            }
          );
        }
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
