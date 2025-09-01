// Mocks before requiring the module
jest.mock('i18next', () => {
  return {
    use: jest.fn().mockReturnThis(),
    init: jest.fn().mockReturnThis(),
  };
});

jest.mock('i18next-http-backend', () => jest.fn());
jest.mock('i18next-browser-languagedetector', () => jest.fn());
jest.mock('react-i18next', () => ({
  initReactI18next: {},
}));

describe('i18n initialization', () => {
  const OLD_ENV = { ...window._env_ };

  beforeEach(() => {
    jest.clearAllMocks();
    window._env_ = { DEFAULT_LANG: 'en' };
    process.env.PUBLIC_URL = 'http://localhost';
  });

  afterAll(() => {
    window._env_ = OLD_ENV;
  });

  it('should configure i18n with expected plugins and settings', () => {
    const i18next = require('i18next');
    const Backend = require('i18next-http-backend');
    const LanguageDetector = require('i18next-browser-languagedetector');
    const { initReactI18next } = require('react-i18next');

    require('../i18n'); // Import after mocks are applied

    expect(i18next.use).toHaveBeenCalledWith(Backend);
    expect(i18next.use).toHaveBeenCalledWith(LanguageDetector);
    expect(i18next.use).toHaveBeenCalledWith(initReactI18next);
    expect(i18next.init).toHaveBeenCalledWith({
      debug: false,
      fallbackLng: 'en',
      interpolation: {
        escapeValue: false,
      },
      backend: {
        loadPath: 'http://localhost/locales/{{lng}}.json',
      },
    });
  });
});
