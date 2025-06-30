import { Buffer } from "buffer";

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