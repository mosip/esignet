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
import { LOGIN } from "./constants/routes";
import { returnToRelyingParty } from "./utils/session-recovery";

const searchParams = new URL(window.location.href).searchParams;

// Normalize the trailing slash so /signin/ matches like the router does.
const isLoginRoute = window.location.pathname.replace(/\/+$/, "").endsWith(LOGIN);

// From the RP hand-off URL; absent after the SDK strips it or on reload/back.
const applicationId = searchParams.get("applicationId") ?? undefined;

// OIDC ui_locales is a space-separated, preference-ordered list; take the first.
const uiLocales = searchParams.get("ui_locales");
const uiLocalesLanguage = uiLocales?.trim().split(/\s+/)[0] || undefined;

// Fall back to DEFAULT_LANG env when ui_locales is absent.
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
const baseUrl = baseUrlRaw || `http://localhost:8088`;

if (isLoginRoute && !applicationId) {
  // Reload/back with no live transaction → hand back to the RP.
  returnToRelyingParty();
} else {
  createRoot(document.getElementById("root")!).render(
    <StrictMode>
      {applicationId ? (
        <ThunderIDProvider
          baseUrl={baseUrl}
          applicationId={applicationId}
          namespace={applicationId}
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
}
