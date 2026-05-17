import { useEffect, useState, useRef } from "react";
import { init, propChange } from "@mosip/secure-biometric-interface-integrator";
import { encodeBase64 } from "../utils/encoding";
import type {
  ComponentRenderContext,
  EmbeddedFlowComponent,
} from "@asgardeo/react";

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

export default function Sbi({ component, context }: SbiProps) {
  const firstRender = useRef(true);
  const [, setValue] = useState("");

  useEffect(() => {
    if (firstRender.current) {
      firstRender.current = false;
      init({
        container: document.getElementById(SBI_CONTAINER_ID),
        buttonLabel: "scan_and_verify",
        transactionId: "transactionId",
        sbiEnv: DEFAULT_SBI_ENV,
        langCode: "en",
        disable: false,
      });
      return;
    }

    propChange({
      onCapture: (response: BiometricResponse | null) =>
        authenticateBiometricResponse(response),
    });
  }, []);

  const onInputChangeRef = useRef(context.onInputChange);
  onInputChangeRef.current = context.onInputChange;

  useEffect(() => {
    onInputChangeRef.current(component.id, "");
  }, [component.id]);

  /**
   * Validates the SBI capture response and, if valid, encodes
   * the biometrics payload and passes it to the form context.
   */
  const authenticateBiometricResponse = async (
    biometricResponse: BiometricResponse | null,
  ): Promise<void> => {
    const { errorCode } = validateBiometricResponse(biometricResponse);

    if (errorCode !== null) return;

    const encoded = encodeBase64(
      JSON.stringify(biometricResponse?.biometrics ?? []),
    );
    setValue(encoded);
    context.onInputChange(component.id, encoded);
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
      <form className="relative">
        <div id="secure-biometric-interface-integration" className="my-2"></div>
      </form>
    </>
  );
}
