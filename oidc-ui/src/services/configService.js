import axios from 'axios';
import { CONFIG_URLS } from '../constants/publicAssets';

const configService = async () => {
  const response = await axios.get(CONFIG_URLS.THEME_CONFIG);
  return response.data;
};

export default configService;
