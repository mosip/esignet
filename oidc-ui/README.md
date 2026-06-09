# eSignet OIDC UI

This is the user interface for eSignet Services, implementing OpenID Connect specifications. Built with React 19, TypeScript, Vite, and Tailwind CSS.

## Pages

| Route | Page | Description |
|-------|------|-------------|
| `/` | eSignet Details | Displays well-known endpoints from runtime configuration |
| `/login/signin` | Login | Authentication interface for sign-in |
| `/something-went-wrong` | Error | Generic error page with status code display |
| `/network-error` | Network Error | Shown when the app is offline or API is unreachable |
| `*` | Page Not Found | Catch-all for unmatched routes |

## Tech Stack

- **Framework:** React 19 + TypeScript (strict mode)
- **Build Tool:** Vite
- **Styling:** Tailwind CSS 4 + CSS custom properties
- **Routing:** React Router v7
- **Server State:** TanStack React Query
- **HTTP Client:** Axios (with CSRF token handling)
- **i18n:** i18next with browser language detection and HTTP backend
- **JWT/Crypto:** `jose` + Web Crypto API (no `crypto-js`)
- **UI Components:** Radix UI (dropdown menu, popover)
- **Testing:** Vitest + React Testing Library

## Prerequisites

- Node.js >= 18
- npm >= 9

## Getting Started

### Install dependencies

```bash
npm install
```

### Local development

```bash
npm run dev
```

The dev server starts at `http://localhost:5173` by default.

### Environment configuration

Runtime configuration is managed through `public/env-config.js`:

```javascript
window._env_ = {
  DEFAULT_LANG: 'en',
  DEFAULT_WELLKNOWN: '<encoded JSON array of well-known endpoints>',
  DEFAULT_THEME: '',
  DEFAULT_FAVICON: 'favicon.ico',
  DEFAULT_TITLE: 'eSignet',
  DEFAULT_ID_PROVIDER_NAME: 'eSignet',
  DEFAULT_FONT_URL: 'https://fonts.googleapis.com/css2?family=Montserrat:wght@300;400;500;600;700&display=swap',
};
```

Optional polling configuration:

| Variable | Default | Description |
|----------|---------|-------------|
| `POLLING_URL` | `/v1/esignet/actuator/health` | Health check endpoint |
| `POLLING_INTERVAL` | `10000` | Polling interval in ms |
| `POLLING_TIMEOUT` | `5000` | Polling timeout in ms |
| `POLLING_ENABLED` | `true` | Enable/disable network polling |

## Available Scripts

| Command | Description |
|---------|-------------|
| `npm run dev` | Start Vite dev server with HMR |
| `npm run build` | TypeScript check + production build |
| `npm run preview` | Preview production build locally |
| `npm run test` | Run all tests with Vitest |
| `npm run test:coverage` | Run tests with coverage report |
| `npm run typecheck` | TypeScript type checking only |
| `npm run lint` | Run ESLint |

## Theme Customization

To customize the theme, modify the CSS custom properties in `public/theme/variables.css`. Key variables include:

- `--primary-color` — Primary brand color (default: `#1262c9`)
- `--brand-logo-url` — Header brand logo
- `--footer-brand-logo-url` — Footer logo
- `--login-background` — Login page background color
- `--primary-button-*` — Primary button colors and states
- `--secondary-button-*` — Secondary button colors and states

UI feature toggles are configured in `public/theme/config.json`:

```json
{
  "background_logo": false,
  "footer": true,
  "remove_language_indicator_pipe": true,
  "outline_toggle": false,
  "outline_dropdown": false
}
```

## Internationalization

Supported languages are configured in `public/locales/default.json`. Translation files are in `public/locales/`:

- `en.json` — English
- `ar.json` — Arabic (RTL)
- `hi.json` — Hindi
- `kn.json` — Kannada
- `km.json` — Khmer
- `ta.json` — Tamil

RTL languages are automatically detected and the `dir` attribute is set accordingly.

## Project Structure

```text
oidc-ui/
├── index.html                # Entry HTML
├── vite.config.ts            # Vite + Tailwind + Vitest config
├── tsconfig.json             # TypeScript config
├── package.json
├── public/
│   ├── env-config.js         # Runtime environment config
│   ├── theme-config.js       # Theme initialization script
│   ├── theme/
│   │   ├── variables.css     # CSS custom properties
│   │   └── config.json       # UI feature toggles
│   ├── locales/              # i18n translation files
│   └── images/               # Static image assets
└── src/
    ├── main.tsx              # Entry point
    ├── App.tsx               # Root component (i18n, QueryClient, RTL)
    ├── App.css               # Global styles + Tailwind
    ├── i18n.ts               # i18next configuration
    ├── types/                # Shared TypeScript interfaces
    ├── constants/            # Route paths, loading states, asset URLs
    ├── utils/                # Encoding, hashing (Web Crypto), parsing
    ├── services/             # API, config, CSS variable, lang config
    ├── components/           # NavHeader, Footer, LoadingIndicator
    ├── pages/                # Page components (Login, Error, 404, etc.)
    ├── routes/               # AppRouter with lazy-loaded routes
    └── __tests__/            # Test files mirroring src structure
```
