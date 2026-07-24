import { useContext } from "react";
import { I18nContext } from "@thunderid/react";

/**
 * English fallback translations used when ThunderIDProvider is not available.
 * This ensures components render readable text even outside the SDK context
 * (e.g., when accessing /esignet-details without applicationId query param).
 */
const FALLBACK_EN: Record<string, string> = {
  "app.footer.powered_by": "Powered by",
  "app.network_error.title": "No Internet Connection",
  "app.network_error.description": "Please check your network connection and try again.",
  "app.network_error.try_again": "Try Again",
  "app.otp.resend_timer": "You can resend the OTP in",
  "app.otp.timed_out": "Timed out",
  "app.otp.submit": "Submit",
  "app.back": "Back",
  "app.esignet_details": "eSignet Details",
  "errors.heading": "Error",
  "errors.something_went_wrong": "Something went wrong",
  "errors.unexpected_error": "An unexpected error occurred. Please try again later.",
  "errors.page_not_found": "Page Not Found",
  "errors.network.unavailable": "Network unavailable. Please check your connection.",
  "messages.loading.placeholder": "Loading...",
};

/**
 * Safe translation hook that works both inside and outside ThunderIDProvider.
 *
 * When inside ThunderIDProvider (login flow), returns the SDK's `t()` function
 * which resolves translations from the i18n bundle served by the meta endpoint.
 *
 * When outside ThunderIDProvider (e.g., /esignet-details without applicationId),
 * returns a function that looks up English fallback strings.
 */
export function useAppTranslation() {
  const i18n = useContext(I18nContext);

  if (i18n) {
    return { t: i18n.t, isTranslationAvailable: true };
  }

  const fallbackT = (key: string): string => FALLBACK_EN[key] ?? key;

  return { t: fallbackT, isTranslationAvailable: false };
}
