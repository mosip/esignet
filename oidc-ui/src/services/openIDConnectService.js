import { Buffer } from "buffer";
import { sha256 } from 'crypto-hash';

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
    return this.oAuthDetails.redirect_uri;
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
  getIdpConfiguration = (configKey) => {
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
   * @returns Base64 encoded SHA-256 hash of the oauth-details endpoint response.
   */
  getOauthDetailsHash = async () => {
    let oAuthDetailsStr = JSON.stringify(this.oAuthDetails);
    let oAuthDetailsB64 = Buffer.from(oAuthDetailsStr).toString("base64");
    return await sha256(oAuthDetailsB64);
  };
};

export default openIDConnectService;