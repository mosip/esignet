# Feature: Greenfield UI — `oidc-ui`

## Problem Statement

The existing `oidc-ui` application uses Create React App (unmaintained), plain JavaScript, and carries legacy dependencies like `crypto-js` (unmaintained, known vulnerabilities). A new greenfield React application — `oidc-ui` — will be created under `esignet/` with TypeScript, Vite, and modern tooling while preserving the visual identity (CSS theme, variables, images) of the existing app.

This is **not** a migration of `oidc-ui`. It is a new project that cherry-picks only what is needed from the old app and rewrites everything else in TypeScript.

---

## Goals

1. **Greenfield React + TypeScript project** scaffolded with Vite (no Webpack, no CRA).
2. **Preserve look and feel** — carry over theme CSS variables (`variables.css`), core app styles (`App.css`), and image assets from the existing `oidc-ui/public/` folder.
3. **Tailwind CSS integration** — convert carried-over CSS variables into Tailwind theme tokens where practical; keep raw CSS only for variables that must remain dynamic at runtime.
4. **Rewrite utilities and services in TypeScript** — `utils.js`, `configService.js`, `cssVariableService.js` must be rewritten (not copied) in TypeScript following current security best practices.
5. **Drop `crypto-js`** — use the Web Crypto API (`SubtleCrypto`) for SHA-256 hashing and `jose` for all JWT/JWS operations.
6. **Minimal routing** — login page + generic error pages (something-went-wrong, network-error, page-not-found). No consent, biometric, or OTP routes in this phase.
7. **NavHeader component** — rewrite the existing `NavHeader.js` as a TypeScript component, retaining language selector and brand logo functionality.
8. **Test coverage >= 80%** on branches, functions, lines, and statements.

---

## Tech Stack

| Category | Choice |
|---|---|
| Language | TypeScript (strict mode) |
| Framework | React 18+ |
| Build tool | Vite (dev server, build, and production bundling) |
| Styling | Tailwind CSS 3+ with carried-over CSS custom properties |
| Routing | react-router-dom v6+ |
| HTTP client | axios |
| Server state | @tanstack/react-query |
| JWT / Crypto | `jose` + Web Crypto API (`SubtleCrypto`) |
| UI primitives | @radix-ui/react-popover, @radix-ui/react-dropdown-menu |
| Auth SDK | @asgardeo/react |
| Testing | Vitest + React Testing Library |
| Linting | ESLint + Prettier |

---

## Project Structure

```
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

---

## Routes

| Path | Component | Description |
|---|---|---|
| `/` | `EsignetDetailsPage` | Displays well-known endpoints parsed from `window._env_.DEFAULT_WELLKNOWN` |
| `/login/signin` | `LoginPage` | Login page — the primary route |
| `/something-went-wrong` | `SomethingWrongPage` | Generic error page |
| `/network-error` | `NetworkErrorPage` | Shown when offline or API unreachable |
| `*` | `PageNotFoundPage` | Catch-all for unmatched routes |

The root `/` route shows the eSignet details page with brand logo and clickable well-known endpoint links. Unmatched routes show the 404 page.

---

## What to Carry Over from `oidc-ui`

### Copy as-is (assets only)
- `public/images/` — all image assets (~60 files: logos, icons, illustrations)
- `public/theme/config.json` — feature toggle config
- `public/theme/variables.css` — 100+ CSS custom properties for theming
- `public/locales/` — i18n JSON language files
- `public/env-config.js` — runtime `window._env_` configuration

### Copy and adapt (CSS)
- `src/App.css` — carry over, then clean up unused classes (consent, biometric, KBI form styles are not needed in this phase). Ensure all class references use the CSS variables from `variables.css`.
- **Fix horizontal scroll overflow**: add `overflow-x: hidden` to `.section-background` and `max-width: 100%; box-sizing: border-box;` to `.multipurpose-login-card` to prevent the card and absolute-positioned background logo elements from causing horizontal page overflow.

### Rewrite in TypeScript
| Old file (JS) | New file (TS) | Notes |
|---|---|---|
| `src/helpers/utils.js` | `src/utils/encoding.ts`, `hashing.ts`, `parsing.ts` | Split by concern. Replace `crypto-js` SHA-256 with `SubtleCrypto.digest()`. Replace `Buffer` usage with `btoa`/`atob` or `TextEncoder`. |
| `src/services/configService.js` | `src/services/config.service.ts` | Type the config shape. Fetch from `/theme/config.json`. |
| `src/services/cssVariableService.js` | `src/services/css-variable.service.ts` | Inject CSS variables onto `document.documentElement`. Type the variable map. |
| `src/services/langConfigService.js` | `src/services/lang-config.service.ts` | Type locale config. Keep RTL detection logic. |
| `src/services/api.service.js` | `src/services/api.service.ts` | Typed axios instance. Keep CSRF token fetch + header injection. Replace custom `HttpError` class with typed error. |
| `src/components/NavHeader.js` | `src/components/NavHeader.tsx` | Rewrite with proper props interface. Keep language dropdown (Radix UI). Keep brand logo. |
| `src/helpers/redirectOnError.js` | `src/utils/redirect-on-error.ts` | Type the OAuth error redirect flow. |
| `src/pages/EsignetDetails.js` + `src/components/EsignetDetails.js` | `src/pages/EsignetDetailsPage.tsx` | Merge page wrapper and component into a single typed component. Parse `window._env_.DEFAULT_WELLKNOWN`, render well-known endpoint links. |
| Error page components | `src/pages/*.tsx` | Rewrite from the existing `SomethingWrongPage.js`, `NetworkError.js`, `PageNotFound.js`. |

---

## Security Requirements

- **No `crypto-js`**: use `await crypto.subtle.digest('SHA-256', data)` for hashing.
- **CSRF**: the axios instance must fetch and attach `X-XSRF-TOKEN` headers (preserve existing pattern from `api.service.js`).
- **No secrets in client code**: all sensitive config comes from `env-config.js` at runtime, not baked into the bundle.
- **Strict TypeScript**: enable `strict: true` in `tsconfig.json`.

---

## Tasks

- [ ] **Scaffold project**: `npm create vite@latest oidc-ui -- --template react-ts` under `esignet/`. Configure `vite.config.ts` with appropriate base path and dev server proxy if needed.
- [ ] **Install dependencies**: react-router-dom, axios, @tanstack/react-query, jose, @radix-ui/react-popover, @radix-ui/react-dropdown-menu, @asgardeo/react, i18next, react-i18next, i18next-browser-languagedetector, i18next-http-backend.
- [ ] **Install dev dependencies**: tailwindcss, postcss, autoprefixer, vitest, @testing-library/react, @testing-library/jest-dom, @testing-library/user-event, eslint, prettier.
- [ ] **Configure Tailwind**: set up `tailwind.config.ts` extending theme with tokens from `variables.css` where static values exist. Keep `variables.css` loaded globally for dynamic/runtime values.
- [ ] **Copy assets**: images, theme config, variables.css, locales, env-config.js from `oidc-ui/public/` into `oidc-ui/public/`.
- [ ] **Copy and clean App.css**: bring over `App.css`, remove classes for consent, biometric, KBI, and other unused features.
- [ ] **Rewrite services in TypeScript**: `config.service.ts`, `css-variable.service.ts`, `lang-config.service.ts`, `api.service.ts` — all with proper types and interfaces.
- [ ] **Rewrite utilities in TypeScript**: split `utils.js` into `encoding.ts` (base64url), `hashing.ts` (SHA-256 via SubtleCrypto), `parsing.ts`. Rewrite `redirectOnError.js` as `redirect-on-error.ts`.
- [ ] **Build App.tsx**: root component with i18n initialization, QueryClient setup, CSS variable injection, and RTL support.
- [ ] **Build NavHeader.tsx**: brand logo + language selector dropdown using Radix UI.
- [ ] **Build AppRouter.tsx**: five routes — `/` (EsignetDetails), `/login/signin`, `/something-went-wrong`, `/network-error`, `*` (404).
- [ ] **Build page components**: `EsignetDetailsPage.tsx` (well-known endpoints from `window._env_.DEFAULT_WELLKNOWN`), `LoginPage.tsx` (can use dummy data initially), `SomethingWrongPage.tsx`, `NetworkErrorPage.tsx`, `PageNotFoundPage.tsx` — each displaying appropriate UI with images from the carried-over assets.
- [ ] **Verify app runs**: `pnpm dev` starts successfully; all four routes render correctly.
- [ ] **Write tests**: Vitest + React Testing Library tests for all components, services, and utilities. Target >= 80% coverage on branches, functions, lines, and statements.
- [ ] **Lint and type-check**: ESLint and `tsc --noEmit` pass with zero errors.

---

## Acceptance Criteria

1. `pnpm dev` starts the app without errors.
2. `pnpm build` produces a production bundle via Vite.
3. All routes (`/login/signin`, `/something-went-wrong`, `/network-error`, `/*`) render correctly with their respective UI and images.
4. Login page displays with the same visual styling (colors, layout, logo) as the existing `oidc-ui` login page — dummy data is acceptable for form fields and interactions.
5. NavHeader shows brand logo and functional language selector.
6. No `crypto-js` in `package.json` — all crypto uses Web Crypto API or `jose`.
7. `pnpm test` passes with >= 80% coverage (branches, functions, lines, statements).
8. `pnpm lint` and TypeScript type-check pass with zero errors.
9. All source files are `.ts` or `.tsx` — no `.js`/`.jsx` in `src/`.

---

## Out of Scope (future phases)

- Consent page, OTP page, biometric authentication flows
- Linked authorization / QR code flows
- Wallet login (WLA)
- SBI (Secure Biometric Interface) integration
- Form builder integration (`@mosip/json-form-builder`)
- E2E tests (Playwright)
- Docker / Nginx deployment config
- Integration with live eSignet backend APIs
