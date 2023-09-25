import axios from "axios";
import localStorageService from "./local-storageService";

const baseUrl =
  process.env.NODE_ENV === "development"
    ? process.env.REACT_APP_ESIGNET_API_URL
    : window.origin + process.env.REACT_APP_ESIGNET_API_URL;
const proxyAuthCodeEndpoint = "/proxy/auth-code";

const { getCookie } = { ...localStorageService };
 /**
   * Triggers /proxy/auth-code API to esignet service
   * @param {String} authCode
   * @param {String} transactionId
   * @returns /proxy-auth-code API response
   */
const post_proxyAuthCode = async (authCode, transactionId) => {
  let request = {
    requestTime: new Date().toISOString(),
    request: {
      transactionId: transactionId,
      authCode: authCode
    },
  };

  let endpoint = baseUrl + proxyAuthCodeEndpoint;
  let response = await axios.post(endpoint, request, {
    headers: {
      "Content-Type": "application/json",
      "X-XSRF-TOKEN": getCookie("XSRF-TOKEN")
    },
  });
  return response.data;
};

const relyingPartyService = {
  post_proxyAuthCode,
};
export default relyingPartyService;
