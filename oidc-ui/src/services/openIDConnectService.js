import { Buffer } from "buffer";
import sha256 from 'crypto-js/sha256';
import Base64 from 'crypto-js/enc-base64';

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
}

export default openIDConnectService;
