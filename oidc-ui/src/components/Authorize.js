import React from "react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate, useSearchParams } from "react-router-dom";
import ErrorIndicator from "../common/ErrorIndicator";
import LoadingIndicator from "../common/LoadingIndicator";
import { LoadingStates as states } from "../constants/states";

export default function Authorize({
  authService,
  langConfigService,
  i18nKeyPrefix = "authorize",
}) {
  const { i18n } = useTranslation("translation", {
    keyPrefix: i18nKeyPrefix,
  });

  const get_CsrfToken = authService.get_CsrfToken;
  const post_OauthDetails = authService.post_OauthDetails;
  const buildRedirectParams = authService.buildRedirectParams;

  const { getLocaleConfiguration } = {
    ...langConfigService,
  };

  const [status, setStatus] = useState(states.LOADING);
  const [oAuthDetailResponse, setOAuthDetailResponse] = useState(null);
  const [error, setError] = useState(null);
  const [searchParams, setSearchParams] = useSearchParams();

  const navigate = useNavigate();

  useEffect(() => {
    const callAuthorize = async () => {
      try {
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
          uiLocales
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
      changeLanguage();
      redirectToLogin();
    }
  }, [status]);

  const changeLanguage = async () => {
    //Language detector priotity order: ['querystring', 'cookie', 'localStorage',
    //      'sessionStorage', 'navigator', 'htmlTag', 'path', 'subdomain'],

    //1. Check for ui locales param. Highest priority.
    //This will override the language detectors selected language
    let defaultConfigs = await getLocaleConfiguration();
    let supportedLanguages = defaultConfigs.languages_2Letters;
    let uiLocales = searchParams.get("ui_locales");
    if (uiLocales) {
      let languages = uiLocales.split(" ");
      for (let idx in languages) {
        if (supportedLanguages[languages[idx]]) {
          i18n.changeLanguage(languages[idx]);
          return;
        }
      }

      // if language code not found in 2 letter codes, then check mapped language codes
      let langCodeMapping = defaultConfigs.langCodeMapping;
      for (let idx in languages) {
        if (langCodeMapping[languages[idx]]) {
          i18n.changeLanguage(langCodeMapping[languages[idx]]);
          return;
        }
      }
    }

    //2. Check for cookie
    //Language detector will store and use cookie "i18nextLng"

    //3. Check for system locale
    //Language detector will check navigator and subdomain to select proper language

    //4. default lang set in env_configs file as fallback language.
  };

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

        navigate("/login" + params, {
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
