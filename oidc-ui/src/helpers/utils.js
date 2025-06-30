import { Buffer } from "buffer";
import sha256 from "crypto-js/sha256";
import Base64 from "crypto-js/enc-base64";

const encodeString = (str) => {
    return Buffer.from(str).toString("base64");
}

const decodeHash = (hash) => {
    return Buffer.from(hash, "base64").toString();
}

/**
 * Take a config object and a property name, and check if the property exists
 * in the config object. If it does, return true, otherwise return false.
 * @param object config 
 * @param string property 
 * @returns boolean
 */
const checkConfigProperty = (config, property) => {
    if (config && (property in config)) {
        return true;
    }
    return false;
}

const getPollingConfig = () => {
  const url =
    window._env_?.POLLING_URL ||
    (process.env.NODE_ENV === "development"
      ? process.env.REACT_APP_ESIGNET_API_URL + "/actuator/health"
      : window.origin + "/v1/esignet/actuator/health");
  const interval = Number(window._env_?.POLLING_INTERVAL) || 10000;
  const timeout = Number(window._env_?.POLLING_TIMEOUT) || 5000;
  const enabled =
    typeof window._env_?.POLLING_ENABLED !== "undefined"
      ? window._env_.POLLING_ENABLED === "true" || window._env_.POLLING_ENABLED === true
      : true;

  return { url, interval, timeout, enabled };
};
export { encodeString, decodeHash, checkConfigProperty, getPollingConfig };
/**
 * Generates a base64url-encoded SHA-256 hash of the given value.
 *
 * @param {Object} value - The value to be hashed. Typically an object representing OAuth details.
 * @returns {Promise<string>} A Promise that resolves to the base64url-encoded hash string.
 */
const getOauthDetailsHash = async (value) => {
  let sha256Hash = sha256(JSON.stringify(value));
  let hashB64 = Base64.stringify(sha256Hash)
    .split("=")[0]
    .replace(/\+/g, "-")
    .replace(/\//g, "_");
  return hashB64;
};

/**
 * Decodes a base64url-encoded string into a UTF-8 string.
 *
 * @param {string} str - The base64url-encoded string (e.g., a JWT part).
 * @returns {string} The decoded UTF-8 string.
 */

const base64UrlDecode = (str) => {
  return decodeURIComponent(
    decodeHash(str.replace(/-/g, "+").replace(/_/g, "/"))
      .split("")
      .map((c) => `%${("00" + c.charCodeAt(0).toString(16)).slice(-2)}`)
      .join("")
  );
};

export { encodeString, decodeHash, checkConfigProperty, getOauthDetailsHash, base64UrlDecode };
