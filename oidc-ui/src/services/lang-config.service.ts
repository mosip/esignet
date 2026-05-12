import axios from 'axios';
import { CONFIG_URLS } from '../constants/public-assets';
import type { LocaleConfig } from '../types';

/**
 * Fetches the locale configuration (language names, RTL list, code mapping).
 */
export async function getLocaleConfiguration(): Promise<LocaleConfig> {
  const response = await axios.get<LocaleConfig>(CONFIG_URLS.LOCALE_CONFIG);
  return response.data;
}

/**
 * Fetches the English locale translations.
 */
export async function getEnLocaleConfiguration(): Promise<
  Record<string, unknown>
> {
  const response = await axios.get<Record<string, unknown>>(
    CONFIG_URLS.ENG_CONFIG,
  );
  return response.data;
}

/**
 * Returns a reversed language code mapping (e.g. { "en": "eng", "hi": "hin" }).
 */
export async function getLangCodeMapping(): Promise<Record<string, string>> {
  const config = await getLocaleConfiguration();
  return Object.entries(config.langCodeMapping).reduce<Record<string, string>>(
    (acc, [key, value]) => {
      acc[value] = key;
      return acc;
    },
    {},
  );
}
