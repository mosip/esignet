import { describe, it, expect, vi, beforeEach } from 'vitest';
import axios from 'axios';
import {
  getLocaleConfiguration,
  getLangCodeMapping,
} from '../../services/lang-config.service';

vi.mock('axios');

const mockLocaleConfig = {
  languages_2Letters: { en: 'English', hi: 'हिंदी', ar: 'عربى' },
  rtlLanguages: ['ar'],
  langCodeMapping: { eng: 'en', hin: 'hi', ara: 'ar' },
};

describe('lang-config.service', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('getLocaleConfiguration', () => {
    it('fetches locale configuration', async () => {
      vi.mocked(axios.get).mockResolvedValue({ data: mockLocaleConfig });

      const result = await getLocaleConfiguration();
      expect(result).toEqual(mockLocaleConfig);
    });
  });

  describe('getLangCodeMapping', () => {
    it('returns a reversed language code mapping', async () => {
      vi.mocked(axios.get).mockResolvedValue({ data: mockLocaleConfig });

      const result = await getLangCodeMapping();
      expect(result).toEqual({ en: 'eng', hi: 'hin', ar: 'ara' });
    });
  });
});
