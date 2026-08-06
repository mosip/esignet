import { useEffect, useState, useRef } from "react";
import { init, propChange } from "@mosip/secure-biometric-interface-integrator";
import { encodeBase64 } from "../../utils/encoding";
import { useTranslation } from "@thunderid/react";
import type {
  ComponentRenderContext,
  EmbeddedFlowComponent,
} from "@thunderid/react";

interface BiometricError {
  errorCode: string;
  errorInfo?: string;
}

interface BiometricEntry {
  error?: BiometricError | null;
  [key: string]: unknown;
}

interface BiometricResponse {
  biometrics?: BiometricEntry[] | null;
}

interface ValidationResult {
  errorCode: string | null;
  defaultMsg: string | null;
}

interface SbiProps {
  component: EmbeddedFlowComponent;
  context: ComponentRenderContext;
}

const SBI_CONTAINER_ID = "secure-biometric-interface-integration";

/** Languages supported by the SBI widget's built-in i18next resources. */
const SBI_SUPPORTED_LANGS = new Set(["en", "ar", "hi", "kn", "ta"]);

/**
 * Maps the app's current language to a
 * 2-letter code the SBI widget understands. Falls back to "en".
 */
const toSbiLangCode = (language: string): string => {
  const base = language.split("-")[0].toLowerCase();
  return SBI_SUPPORTED_LANGS.has(base) ? base : "en";
};

const DEFAULT_SBI_ENV = {
  env: "Staging",
  captureTimeout: 30,
  irisBioSubtypes: "UNKNOWN",
  fingerBioSubtypes: "UNKNOWN",
  faceCaptureCount: 1,
  faceCaptureScore: 80,
  fingerCaptureCount: 1,
  fingerCaptureScore: 80,
  irisCaptureCount: 1,
  irisCaptureScore: 80,
  portRange: "4501-4600",
  discTimeout: 15,
  dinfoTimeout: 30,
  domainUri: window.origin,
} as const;

export default function SbiComponent({ component, context }: SbiProps) {
  const [, setValue] = useState("");
  const fieldRef = component.ref ?? component.id;
  const { currentLanguage } = useTranslation();
  const sbiLangCode = toSbiLangCode(currentLanguage);
  const isInitialized = useRef(false);

  useEffect(() => {
    const primaryColor = getComputedStyle(document.documentElement)
      .getPropertyValue("--primary-color")
      .trim();
    const customStyle = {
      verifyButtonStyle: {
        background: primaryColor,
        color: "white",
      },
    };
    init({
      container: document.getElementById(SBI_CONTAINER_ID),
      buttonLabel: "scan_and_verify",
      transactionId: "transactionId",
      sbiEnv: DEFAULT_SBI_ENV,
      langCode: sbiLangCode,
      disable: false,
      customStyle,
    });

    propChange({
      onCapture: (response: BiometricResponse | null) =>
        authenticateBiometricResponse(response),
    });

    isInitialized.current = true;
  }, []);

  // Reactively update the SBI widget language when app language changes
  useEffect(() => {
    if (!isInitialized.current) return;
    propChange({ langCode: sbiLangCode });
  }, [sbiLangCode]);

  const onInputChangeRef = useRef(context.onInputChange);
  onInputChangeRef.current = context.onInputChange;

  useEffect(() => {
    onInputChangeRef.current(fieldRef, "");
  }, [fieldRef]);

  /**
   * Validates the SBI capture response and, if valid, encodes
   * the biometrics payload and passes it to the form context.
   *
   * component.ref will be the property name
   */
  const authenticateBiometricResponse = async (
    biometricResponse: BiometricResponse | null,
  ): Promise<void> => {
    const { errorCode } = validateBiometricResponse(biometricResponse);

    if (errorCode !== null) {
      setValue("");
      context.onInputChange(fieldRef, "");
      return;
    }

    const encoded = encodeBase64(
      JSON.stringify(biometricResponse?.biometrics ?? []),
    );
    setValue(encoded);
    context.onInputChange(fieldRef, encoded);

    if (context.onSubmit) {
      // submitting the whole form, while click on
      // scan & verify button of biometric component
      context.onSubmit(component, { [fieldRef]: encoded }, true);
    }
  };

  /**
   * Validates an SBI capture response.
   * Returns the first non-zero error found, or null errorCode if all entries are valid.
   * Removes the `error` property from valid biometric entries.
   */
  const validateBiometricResponse = (
    response: BiometricResponse | null,
  ): ValidationResult => {
    const biometrics = response?.biometrics;

    if (!biometrics?.length) {
      return { errorCode: "no_response_msg", defaultMsg: null };
    }

    for (const entry of biometrics) {
      if (entry.error && entry.error.errorCode !== "0") {
        return {
          errorCode: entry.error.errorCode,
          defaultMsg: entry.error.errorInfo ?? null,
        };
      }
      delete entry.error;
    }

    return { errorCode: null, defaultMsg: null };
  };

  return (
    <>
      <div className="relative">
        <div id="secure-biometric-interface-integration" className="my-2"></div>
      </div>
    </>
  );
}
