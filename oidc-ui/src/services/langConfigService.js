import axios from "axios";

const defaultConfigEndpoint = "/locales/default.json";
const engConfigEndpoint = "/locales/en.json";

/**
 * fetchs and return the locale configuration stored in public folder
 * @returns json object
 */
const getLocaleConfiguration = async () => {
  const endpoint = process.env.PUBLIC_URL + defaultConfigEndpoint;

  const response = await axios.get(endpoint);
  return response.data;
};

const getEnLocaleConfiguration = async () => {
  const response = await axios.get(engConfigEndpoint);
  return response.data;
};

const getLangCodeMapping = async () => {
  let localConfig = await getLocaleConfiguration();
  let reverseMap = Object.entries(localConfig.langCodeMapping).reduce(
    (pv, [key, value]) => ((pv[value] = key), pv),
    {}
  );
  return reverseMap;
};

const langConfigService = {
  getLocaleConfiguration,
  getLangCodeMapping,
  getEnLocaleConfiguration
};

export default langConfigService;
