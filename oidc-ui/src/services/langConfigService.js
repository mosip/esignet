import axios from 'axios';
import { CONFIG_URLS } from '../constants/publicAssets';

/**
 * fetchs and return the locale configuration stored in public folder
 * @returns json object
 */
const getLocaleConfiguration = async () => {
  const response = await axios.get(CONFIG_URLS.LOCALE_CONFIG);
  return response.data;
};

const getEnLocaleConfiguration = async () => {
  const response = await axios.get(CONFIG_URLS.ENG_CONFIG);
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
  getEnLocaleConfiguration,
};

export default langConfigService;
