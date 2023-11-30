import { Buffer } from "buffer";
import sha256 from "crypto-js/sha256";
import Base64 from "crypto-js/enc-base64";
import {
  walletConfigKeys,
  validAuthFactors,
  configurationKeys,
  modalityIconPath,
} from "../constants/clientConstants";

class openIDConnectService {
  constructor(oAuthDetails, nonce, state) {
    this.oAuthDetails = oAuthDetails;
    this.nonce = nonce;
    this.state = state;
  }

  /**
   * @returns redirectUri
   */
  getRedirectUri = () => {
    return this.oAuthDetails.redirectUri;
  };

  /**
   * @returns nonce
   */
  getNonce = () => {
    return this.nonce;
  };

  /**
   * @returns state
   */
  getState = () => {
    return this.state;
  };

  /**
   * @returns outhDetails
   */
  getOAuthDetails = () => {
    return this.oAuthDetails;
  };

  /**
   * @returns transactionId
   */
  getTransactionId = () => {
    return this.oAuthDetails.transactionId;
  };

  /**
   *
   * @param {string} configKey
   * @returns configuration value of the given config key
   */
  getEsignetConfiguration = (configKey) => {
    return this.oAuthDetails.configs[configKey];
  };

  /**
   * encodes a jsonObject into base64 string
   * @param {jsonObject} jsonObject
   * @returns
   */
  encodeBase64 = (jsonObject) => {
    let objJsonStr = JSON.stringify(jsonObject);
    let objJsonB64 = Buffer.from(objJsonStr).toString("base64");
    return objJsonB64;
  };
  
  /**
   * encodes a jsonObject into base64 string
   * @param {jsonObject} jsonObject
   * @returns Base64 URL encoded SHA-256 hash of the oauth-details endpoint response.
   */
  getOauthDetailsHash = async () => {
    let sha256Hash = sha256(JSON.stringify(this.oAuthDetails));
    let hashB64 = Base64.stringify(sha256Hash);
    // Remove padding characters
    hashB64 = hashB64.replace(/=+$/, "");
    // Replace '+' with '-' and '/' with '_' to convert to base64 URL encoding
    hashB64 = hashB64.replace(/\+/g, "-").replace(/\//g, "_");
    return hashB64;
  };

  /**
   * Convert wallet object to common authfactor format
   * @param {walletConfigKeys} wla an object with name, logo url, deep-link-uri & download-uri
   * @returns common object for authfactor detail with label, value, icon & id
   */
  wlaToAuthfactor = (wla) => {
    return {
      label: wla[walletConfigKeys.walletName],
      value: { ...wla, type: "WLA" },
      icon: wla[walletConfigKeys.walletLogoUrl],
      id: `login_with_${wla[walletConfigKeys.walletName]
        .replace(" ", "_")
        .toLowerCase()}`,
    };
  };

  /**
   * Convert an authfactor to a common authfactor format
   * @param {Object[]} authFactor an array with single object, with type, subtypes & count
   * @returns common object for authfactor detail with label, value, icon & id
   */
  toAuthfactor = (authFactor) => {
    return {
      label: authFactor[0].type,
      value: authFactor[0],
      icon: modalityIconPath[authFactor[0].type],
      id: `login_with_${authFactor[0].type.toLowerCase()}`,
    };
  };

  /**
   * Get list of authfactor applicable for the current client
   * @returns list of authfactor
   */
  getAuthFactorList = () => {
    let loginOptions = [];
    const wlaList =
      this.getEsignetConfiguration(configurationKeys.walletConfig) ??
      process.env.REACT_APP_WALLET_CONFIG;
    this.oAuthDetails?.authFactors.forEach((authFactor) => {
      const authFactorType = authFactor[0].type;
      if (validAuthFactors[authFactorType]) {
        if (authFactorType === validAuthFactors.WLA) {
          wlaList.forEach((wla) =>
            loginOptions.push(this.wlaToAuthfactor(wla))
          );
        } else {
          loginOptions.push(this.toAuthfactor(authFactor));
        }
      }
    });
    return loginOptions;
  };
}

export default openIDConnectService;
