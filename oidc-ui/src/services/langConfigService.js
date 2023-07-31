import axios from "axios";

const defaultConfigEndpoint = "/locales/default.json";

/**
 * fetchs and return the locale configuration stored in public folder
 * @returns json object
 */
const getLocaleConfiguration = async () => {
  const endpoint = process.env.PUBLIC_URL + defaultConfigEndpoint;

  const response = await axios.get(endpoint);
  return response.data;
};

const langConfigService = {
  getLocaleConfiguration,
};

export default langConfigService;
