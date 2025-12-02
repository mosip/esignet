import React from 'react';
import ReactDOM from 'react-dom/client';
import './index.css';
import App from './App';
import './i18n';
import 'react-tooltip/dist/react-tooltip.css';

// Central fallback constant (avoid duplication in JS/CSS)
const DEFAULT_PRIMARY_COLOR = '#1262C9';

// Apply theme dynamically from env-config.js
function applyTheme() {
  const root = document.documentElement;

  // _env_ (docker), envConfig (static file) – keep backward compatibility
  const config = window._env_ || window.envConfig || {};

  const themeValue = config.DEFAULT_THEME || DEFAULT_PRIMARY_COLOR;

  // Validate color using a temporary element
  const testEl = document.createElement('div');
  testEl.style.color = themeValue;
  const isValidColor = testEl.style.color !== '';

  if (isValidColor) {
    root.style.setProperty('--primary-color', themeValue);
  } else {
    console.warn(
      `Invalid DEFAULT_THEME value: "${themeValue}". Using fallback color.`
    );
    root.style.setProperty('--primary-color', DEFAULT_PRIMARY_COLOR);
  }
}

applyTheme(); // Run before rendering

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
