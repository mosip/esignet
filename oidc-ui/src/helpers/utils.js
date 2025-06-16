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

const POLLING_BASE_URL =
  process.env.NODE_ENV === "development"
    ? process.env.REACT_APP_ESIGNET_API_URL
    : window.origin + process.env.REACT_APP_ESIGNET_API_URL;
const getPollingConfig = () => {
    const defaultConfig = {
    url: POLLING_BASE_URL + "/actuator/health",
    interval: 10000,
    timeout: 5000,
    enabled: true,
  };

  const storedPollingConfig = sessionStorage.getItem("esignet-polling-config");
  if (storedPollingConfig) {
    try {
      const stored = JSON.parse(storedPollingConfig);
      return {
        url: stored.url ? stored.url + "/v1/esignet/actuator/health" : defaultConfig.url,
        interval: stored.interval ?? defaultConfig.interval,
        timeout: stored.timeout ?? defaultConfig.timeout,
        enabled: stored.enabled ?? defaultConfig.enabled,
      };
    } catch {
      // If parsing fails, fall through to return defaultConfig
    }
  }
  return defaultConfig;
}
export { encodeString, decodeHash, checkConfigProperty, getPollingConfig };