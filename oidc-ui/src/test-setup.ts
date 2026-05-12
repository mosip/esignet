import '@testing-library/jest-dom/vitest';

// Mock window._env_ for tests
window._env_ = {
  DEFAULT_LANG: 'en',
  DEFAULT_WELLKNOWN: '',
  DEFAULT_THEME: '',
  DEFAULT_FEVICON: 'favicon.ico',
  DEFAULT_TITLE: 'eSignet',
  DEFAULT_ID_PROVIDER_NAME: 'eSignet',
  DEFAULT_FONT_URL: '',
};
