import localStorageService from "./local-storageService";
import { Buffer } from "buffer";
import { ApiService } from "./api.service";

import {
  SEND_OTP,
  AUTHENTICATE,
  OAUTH_DETAIL_V2,
  OAUTH_DETAIL_V3,
  AUTHCODE,
  CSRF,
  CLAIM_DETAILS,
  PREPARE_SIGNUP_REDIRECT,
  RESUME,
} from "./../constants/routes";

const authorizeQueryParam = "authorize_query_param";

const { getCookie } = { ...localStorageService };

class authService {
  constructor(openIDConnectService) {
    this.openIDConnectService = openIDConnectService;
  }

  /**
   * Triggers /authenticate API on Esignet service
   * @param {string} transactionId same as Esignet transactionId
   * @param {String} individualId UIN/VIN of the individual
   * @param {List<AuthChallenge>} challengeList challenge list based on the auth type(ie. BIO, PIN, INJI)
   * @param {string} captchaToken captcha token detail
   * @returns /authenticate API response
   */
  post_AuthenticateUser = async (
    transactionId,
    individualId,
    challengeList,
    captchaToken,
    oAuthDetailsHash
  ) => {
    let request = {
      requestTime: new Date().toISOString(),
      request: {
        transactionId: transactionId,
        individualId: individualId,
        challengeList: challengeList,
        captchaToken: captchaToken,
      },
    };
    let response = await ApiService.post(AUTHENTICATE, request, {
      headers: {
        "Content-Type": "application/json",
        "X-XSRF-TOKEN": getCookie("XSRF-TOKEN"),
        "oauth-details-hash":
          (await oAuthDetailsHash) ||
          (await this.openIDConnectService.getOauthDetailsHash()),
        "oauth-details-key":
          transactionId || (await this.openIDConnectService.getTransactionId()),
      },
    });
    return response.data;
  };

  /**
   * Triggers /auth-code API on ESIGNET service
   * @param {string} nonce
   * @param {string} state
   * @param {string} clientId
   * @param {url} redirectUri
   * @param {string} responseType
   * @param {string} scope
   * @param {string} acrValues
   * @param {jsonObject} claims
   * @param {string} claimsLocales
   * @param {string} display
   * @param {int} maxAge
   * @param {string} prompt
   * @param {string} uiLocales
   * @params {string} codeChallenge
   * @params {string} codeChallengeMethod
   * @returns /oauthDetails API response
   */
  post_OauthDetails_v2 = async (params) => {
    let request = {
      requestTime: new Date().toISOString(),
      request: params,
    };

    let response = await ApiService.post(OAUTH_DETAIL_V2, request, {
      headers: {
        "Content-Type": "application/json",
        "X-XSRF-TOKEN": getCookie("XSRF-TOKEN"),
      },
    });
    return response.data;
  };

  post_OauthDetails_v3 = async (params) => {
    let request = {
      requestTime: new Date().toISOString(),
      request: params,
    };

    let response = await ApiService.post(OAUTH_DETAIL_V3, request, {
      headers: {
        "Content-Type": "application/json",
        "X-XSRF-TOKEN": getCookie("XSRF-TOKEN"),
      },
    });
    return response.data;
  };

  /**
   * Triggers /auth-code API to esignet service
   * @param {String} transactionId
   * @param {List<String>} acceptedClaims
   * @param {List<String>} permittedAuthorizeScopes
   * @returns /auth-code API response
   */
  post_AuthCode = async (
    transactionId,
    acceptedClaims,
    permittedAuthorizeScopes,
    oAuthDetailsHash
  ) => {
    let request = {
      requestTime: new Date().toISOString(),
      request: {
        transactionId: transactionId,
        acceptedClaims: acceptedClaims,
        permittedAuthorizeScopes: permittedAuthorizeScopes,
      },
    };

    let response = await ApiService.post(AUTHCODE, request, {
      headers: {
        "Content-Type": "application/json",
        "X-XSRF-TOKEN": getCookie("XSRF-TOKEN"),
        "oauth-details-hash":
          (await oAuthDetailsHash) ||
          (await this.openIDConnectService.getOauthDetailsHash()),
        "oauth-details-key":
          transactionId || (await this.openIDConnectService.getTransactionId()),
      },
    });
    return response.data;
  };

  /**
   * Triggers /send-otp API on esignet service
   * @param {string} transactionId esignet transactionId
   * @param {string} individualId UIN/VIN of the individual
   * @param {List<string>} otpChannels list of channels(ie. phone, email)
   * @returns /send-otp API response
   */
  post_SendOtp = async (
    transactionId,
    individualId,
    otpChannels,
    captchaToken
  ) => {
    let request = {
      requestTime: new Date().toISOString(),
      request: {
        transactionId: transactionId,
        individualId: individualId,
        otpChannels: otpChannels,
        captchaToken: captchaToken,
      },
    };

    let response = await ApiService.post(SEND_OTP, request, {
      headers: {
        "Content-Type": "application/json",
        "X-XSRF-TOKEN": getCookie("XSRF-TOKEN"),
        "oauth-details-hash":
          await this.openIDConnectService.getOauthDetailsHash(),
        "oauth-details-key": await this.openIDConnectService.getTransactionId(),
      },
    });
    return response.data;
  };

  /**
   * Gets triggered for the very first time, before any api call.
   * Triggers /csrf/token API on esignet service
   * @returns csrf token.
   */
  get_CsrfToken = async () => {
    let response = await ApiService.get(CSRF, {
      headers: {
        "Content-Type": "application/json",
      },
    });
    return response.data;
  };

  /**
   * Returns parameters for redirecting
   * @returns params.
   */
  buildRedirectParams = (nonce, state, oauthReponse, consentAction) => {
    let params = "?";
    let authenticationTime = Math.floor(new Date().getTime() / 1000);

    if (nonce) {
      params = params + "nonce=" + nonce + "&";
    }

    if (state) {
      params = params + "state=" + state + "&";
    }

    if (consentAction) {
      params = params + "consentAction=" + consentAction + "&";
      params = params + "authenticationTime=" + authenticationTime + "&";
    }

    //removing last "&" character
    params = params.substring(0, params.length - 1);

    let responseStr = JSON.stringify(oauthReponse);
    let responseB64 = Buffer.from(responseStr).toString("base64");
    params = params + "#" + responseB64;
    return params;
  };

  /**
   * Set Authroize url's query parameter in local storage in encoded form
   * @param {string} queryParam
   */
  storeQueryParam = (queryParam) => {
    const encodedBase64 = Buffer.from(queryParam).toString("base64");
    localStorage.setItem(authorizeQueryParam, encodedBase64);
  };

  /**
   * Get encoded authorize's query param, which is stored in
   * localstorage with "authorize_query_param" key
   * @returns {string} encodedAuthorizeQueryParam
   */
  getAuthorizeQueryParam = () => {
    return localStorage.getItem(authorizeQueryParam) ?? "";
  };

  getClaimDetails = async () => {
    let response = await ApiService.get(CLAIM_DETAILS, {
      headers: {
        "Content-Type": "application/json",
        "X-XSRF-TOKEN": getCookie("XSRF-TOKEN"),
        "oauth-details-hash":
          await this.openIDConnectService.getOauthDetailsHash(),
        "oauth-details-key": await this.openIDConnectService.getTransactionId(),
      },
    });
    return response.data;
  };

  prepareSignupRedirect = async (transactionId, pathFragment) => {
    let request = {
      requestTime: new Date().toISOString(),
      request: {
        transactionId,
        pathFragment,
      },
    };

    let response = await ApiService.post(PREPARE_SIGNUP_REDIRECT, request, {
      headers: {
        "Content-Type": "application/json",
        "X-XSRF-TOKEN": getCookie("XSRF-TOKEN"),
        "oauth-details-hash":
          await this.openIDConnectService.getOauthDetailsHash(),
        "oauth-details-key": await this.openIDConnectService.getTransactionId(),
      },
    });
    return response.data;
  };

  resume = async (transactionId, withError, oAuthDetailsHash) => {
    const requestTime = new Date().toISOString();
    const request = {
      requestTime,
      request: { transactionId, withError },
    };

    const oauthDetailsHash =
      oAuthDetailsHash ||
      (await this.openIDConnectService.getOauthDetailsHash());
    const oauthDetailsKey =
      transactionId || (await this.openIDConnectService.getTransactionId());

    const headers = {
      "Content-Type": "application/json",
      "X-XSRF-TOKEN": getCookie("XSRF-TOKEN"),
      "oauth-details-hash": oauthDetailsHash,
      "oauth-details-key": oauthDetailsKey,
    };

    const response = await ApiService.post(RESUME, request, { headers });

    return response.data;
  };
}

export default authService;
