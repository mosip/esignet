import React from 'react';
import ReactDOM from 'react-dom/client';
import './index.css';
import App from './App';
import './i18n';
import 'react-tooltip/dist/react-tooltip.css';

// Apply theme dynamically from env-config.js
function applyTheme() {
  const root = document.documentElement;
  const config = window._env_ || window.envConfig || {};
  const themeValue = config.DEFAULT_THEME || '#1262C9'; // fallback color
  const testEl = document.createElement('div');
  testEl.style.color = themeValue;
  const isValidColor = testEl.style.color !== '';

  if (isValidColor) {
    root.style.setProperty('--primary-color', themeValue);
  } else {
    console.warn(
      `Invalid DEFAULT_THEME value: "${themeValue}". Using fallback color.`
    );
    root.style.setProperty('--primary-color', '#1262C9');
  }
}

applyTheme(); // Run before rendering

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
