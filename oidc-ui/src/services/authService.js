import axios from "axios";
import localStorageService from "./local-storageService";
import { Buffer } from "buffer";

const baseUrl =
  process.env.NODE_ENV === "development"
    ? process.env.REACT_APP_ESIGNET_API_URL
    : window.origin + process.env.REACT_APP_ESIGNET_API_URL;

const sendOtpEndPoint = "/authorization/send-otp";
const authenticateEndPoint = "/authorization/v2/authenticate";
const oauthDetailsEndPoint = "/authorization/v2/oauth-details";
const authCodeEndPoint = "/authorization/auth-code";
const csrfEndPoint = "/csrf/token";

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
   * @returns /authenticate API response
   */
  post_AuthenticateUser = async (
    transactionId,
    individualId,
    challengeList
  ) => {
    let request = {
      requestTime: new Date().toISOString(),
      request: {
        transactionId: transactionId,
        individualId: individualId,
        challengeList: challengeList,
      },
    };

    let endpoint = baseUrl + authenticateEndPoint;

    let response = await axios.post(endpoint, request, {
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
  post_OauthDetails = async (
    nonce,
    state,
    clientId,
    redirectUri,
    responseType,
    scope,
    acrValues,
    claims,
    claimsLocales,
    display,
    maxAge,
    prompt,
    uiLocales,
    codeChallenge,
    codeChallengeMethod
  ) => {
    let request = {
      requestTime: new Date().toISOString(),
      request: {
        nonce: nonce,
        state: state,
        clientId: clientId,
        redirectUri: redirectUri,
        responseType: responseType,
        scope: scope,
        acrValues: acrValues,
        claims: claims,
        claimsLocales: claimsLocales,
        display: display,
        maxAge: maxAge,
        prompt: prompt,
        uiLocales: uiLocales,
        codeChallenge: codeChallenge,
        codeChallengeMethod: codeChallengeMethod,
      },
    };

    var endpoint = baseUrl + oauthDetailsEndPoint;

    let response = await axios.post(endpoint, request, {
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
    permittedAuthorizeScopes
  ) => {
    let request = {
      requestTime: new Date().toISOString(),
      request: {
        transactionId: transactionId,
        acceptedClaims: acceptedClaims,
        permittedAuthorizeScopes: permittedAuthorizeScopes,
      },
    };

    let endpoint = baseUrl + authCodeEndPoint;
    let response = await axios.post(endpoint, request, {
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

    let endpoint = baseUrl + sendOtpEndPoint;

    let response = await axios.post(endpoint, request, {
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
    let endpoint = baseUrl + csrfEndPoint;

    let response = await axios.get(endpoint, {
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
}

export default authService;
