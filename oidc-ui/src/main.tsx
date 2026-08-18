import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { ThunderIDProvider } from "@thunderid/react";
import App from "./App";
import {
  SbiCustomRenderer,
  ResendOtpRenderer,
  BackButtonRenderer,
  CaptchaRenderer,
} from "./components";

const searchParams = new URL(window.location.href).searchParams;

// getting applicationId from query param to pass it to ThunderIDProvider
const applicationId = searchParams.get("applicationId");

// ui_locales (OIDC) is a space-separated, preference-ordered locale list; take
// the most preferred one and let the backend fall back to English if unsupported.
const uiLocales = searchParams.get("ui_locales");
const uiLocalesLanguage = uiLocales?.trim().split(/\s+/)[0] || undefined;

// Fallback to DEFAULT_LANG environment variable if ui_locales is not provided or empty.
const defaultLanguage = (window as any)._env_?.DEFAULT_LANG || undefined;
const initialLanguage = uiLocalesLanguage || defaultLanguage || undefined;

const baseUrlRaw = import.meta.env.DEV
  ? import.meta.env.VITE_API_URL
  : window.origin + import.meta.env.VITE_API_URL;
if (!baseUrlRaw) {
  console.error(
    "VITE_API_URL environment variable is not set. " +
      "Add it to your .env file (e.g. VITE_API_URL=https://your-api-host:8090).",
  );
}
const baseUrl = baseUrlRaw || `http://localhost:8080`;

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    {applicationId ? (
      <ThunderIDProvider
        baseUrl={baseUrl}
        applicationId={applicationId}
        preferences={
          initialLanguage ? { i18n: { language: initialLanguage } } : undefined
        }
        extensions={{
          components: {
            renderers: {
              SBI_ID: SbiCustomRenderer,
              RESEND_OTP: ResendOtpRenderer,
              BACK_BUTTON: BackButtonRenderer,
              CAPTCHA_BOX: CaptchaRenderer,
            },
          },
        }}
      >
        <App />
      </ThunderIDProvider>
    ) : (
      <App />
    )}
  </StrictMode>,
);
