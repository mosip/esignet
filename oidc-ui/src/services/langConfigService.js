import axios from "axios";
import { DEFAULT_CONFIG, ENG_CONFIG } from "../constants/routes";

/**
 * fetchs and return the locale configuration stored in public folder
 * @returns json object
 */
const getLocaleConfiguration = async () => {
  const response = await axios.get(DEFAULT_CONFIG);
  return response.data;
};

const getEnLocaleConfiguration = async () => {
  const response = await axios.get(ENG_CONFIG);
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
