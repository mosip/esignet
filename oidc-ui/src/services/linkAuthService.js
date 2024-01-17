import localStorageService from "./local-storageService";
import { ApiService } from "./api.service";
import {
  LINK_AUTHORIZATION_CODE,
  LINK_CODE_GENERATE,
  LINK_STATUS,
} from "../constants/routes";

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

    let response = await ApiService.post(LINK_CODE_GENERATE, request, {
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

    let response = await ApiService.post(LINK_STATUS, request, {
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

    let response = await ApiService.post(LINK_AUTHORIZATION_CODE, request, {
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
}

export default linkAuthService;
