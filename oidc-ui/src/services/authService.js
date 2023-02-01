import axios from "axios";
import localStorageService from "./local-storageService";

const baseUrl =
  process.env.NODE_ENV === "development"
    ? process.env.REACT_APP_IDP_API_URL
    : window.origin + process.env.REACT_APP_IDP_API_URL;

const sendOtpEndPoint = "/authorization/send-otp";
const authenticateEndPoint = "/authorization/authenticate";
const oauthDetailsEndPoint = "/authorization/oauth-details";
const authCodeEndPoint = "/authorization/auth-code";
const csrfEndPoint = "/csrf/token";

const { getCookie } = { ...localStorageService };

class authService {
  constructor(oAuthDetails) {
    this.oAuthDetails = oAuthDetails;
  }

  /**
   * Triggers /authenticate API on IDP service
   * @param {string} transactionId same as idp transactionId
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
        "oauth-hash": await this.oAuthDetails.getOauthDetailsHash()
      },
    });
    return response.data;
  };

  /**
   * Triggers /auth-code API on IDP service
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
    uiLocales
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
      },
    };

    var endpoint = baseUrl + oauthDetailsEndPoint;

    let response = await axios.post(endpoint, request, {
      headers: {
        "Content-Type": "application/json",
        "X-XSRF-TOKEN": getCookie("XSRF-TOKEN")
      },
    });
    return response.data;
  };

  /**
   * Triggers /auth-code API to IDP service
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
        "oauth-hash": await this.oAuthDetails.getOauthDetailsHash()
      },
    });
    return response.data;
  };

  /**
   * Triggers /send-otp API on IDP service
   * @param {string} transactionId idp transactionId
   * @param {string} individualId UIN/VIN of the individual
   * @param {List<string>} otpChannels list of channels(ie. phone, email)
   * @returns /send-otp API response
   */
  post_SendOtp = async (transactionId, individualId, otpChannels, captchaToken) => {
    let request = {
      requestTime: new Date().toISOString(),
      request: {
        transactionId: transactionId,
        individualId: individualId,
        otpChannels: otpChannels,
        captchaToken: captchaToken
      },
    };

    let endpoint = baseUrl + sendOtpEndPoint;

    let response = await axios.post(endpoint, request, {
      headers: {
        "Content-Type": "application/json",
        "X-XSRF-TOKEN": getCookie("XSRF-TOKEN"),
        "oauth-hash": await this.oAuthDetails.getOauthDetailsHash()
      },
    });
    return response.data;
  };

  /**
   * Gets triggered for the very first time, before any api call.
   * Triggers /csrf/token API on IDP service
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
}

export default authService;
