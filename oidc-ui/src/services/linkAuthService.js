import axios from "axios";
import localStorageService from "./local-storageService";

const baseUrl =
  process.env.NODE_ENV === "development"
    ? process.env.REACT_APP_ESIGNET_API_URL
    : window.origin + process.env.REACT_APP_ESIGNET_API_URL;

const linkCodeGenerateEndPoint = "/linked-authorization/link-code";
const linkStatusEndPoint = "/linked-authorization/link-status";
const linkAuthorizationCodeEndPoint = "/linked-authorization/link-auth-code";

const { getCookie } = { ...localStorageService };

class linkAuthService {
  constructor(openIDConnectService) {
    this.openIDConnectService = openIDConnectService;
  }

  /**
   * Triggers /linked-authorization/link-code API on Esignet service
   * @param {string} transactionId same as Esignet transactionId
   * @returns /linked-authorization/link-code API response
   */
  post_GenerateLinkCode = async (transactionId) => {
    let request = {
      requestTime: new Date().toISOString(),
      request: {
        transactionId: transactionId,
      },
    };

    let endpoint = baseUrl + linkCodeGenerateEndPoint;
    let response = await axios.post(endpoint, request, {
      headers: {
        "Content-Type": "application/json",
        "X-XSRF-TOKEN": getCookie("XSRF-TOKEN"),
        "oauth-details-hash": await this.openIDConnectService.getOauthDetailsHash(),
        "oauth-details-key": await this.openIDConnectService.getTransactionId()
      },
    });
    return response.data;
  };

  /**
   * Triggers /linked-authorization/link-status API on Esignet service
   * @param {string} transactionId same as Esignet transactionId
   * @param {string} linkCode generated Esignet linkcode
   * @returns /linked-authorization/link-status API response
   */
  post_LinkStatus = async (transactionId, linkCode) => {
    let request = {
      requestTime: new Date().toISOString(),
      request: {
        transactionId: transactionId,
        linkCode: linkCode,
      },
    };

    let endpoint = baseUrl + linkStatusEndPoint;
    let response = await axios.post(endpoint, request, {
      headers: {
        "Content-Type": "application/json",
        "X-XSRF-TOKEN": getCookie("XSRF-TOKEN"),
        "oauth-details-hash": await this.openIDConnectService.getOauthDetailsHash(),
        "oauth-details-key": await this.openIDConnectService.getTransactionId()
      },
    });
    return response.data;
  };

  /**
   * Triggers /linked-authorization/authenticate API on Esignet service
   * @param {string} transactionId same as Esignet transactionId
   * @param {string} linkedCode linked Esignet linkcode
   * @returns /linked-authorization/authenticate API response
   */
  post_AuthorizationCode = async (transactionId, linkedCode) => {
    let request = {
      requestTime: new Date().toISOString(),
      request: {
        transactionId: transactionId,
        linkedCode: linkedCode,
      },
    };

    let endpoint = baseUrl + linkAuthorizationCodeEndPoint;
    let response = await axios.post(endpoint, request, {
      headers: {
        "Content-Type": "application/json",
        "X-XSRF-TOKEN": getCookie("XSRF-TOKEN"),
        "oauth-details-hash": await this.openIDConnectService.getOauthDetailsHash(),
        "oauth-details-key": await this.openIDConnectService.getTransactionId()
      },
    });
    return response.data;
  };
}

export default linkAuthService;
