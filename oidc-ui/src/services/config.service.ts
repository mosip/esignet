import axios from 'axios';
import { CONFIG_URLS } from '../constants/public-assets';
import type { ThemeConfig } from '../types';

/**
 * Fetches the theme configuration from the public theme config file.
 */
export async function fetchThemeConfig(): Promise<ThemeConfig> {
  const response = await axios.get<ThemeConfig>(CONFIG_URLS.THEME_CONFIG);
  return response.data;
}
