import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { ThunderIDProvider } from "@thunderid/react";
import App from "./App";
import SbiCustomRenderer from "./components/SbiCustomRenderer";

// getting applicationId from query param to pass it to ThunderIDProvider
const applicationId = new URL(window.location.href).searchParams.get(
  "applicationId",
);

const baseUrlRaw = import.meta.env.VITE_API_URL;
if (!baseUrlRaw) {
  console.error(
    "VITE_API_URL environment variable is not set. " +
      "Add it to your .env file (e.g. VITE_API_URL=https://your-api-host:8090).",
  );
}
const baseUrl = baseUrlRaw || `https://localhost:8088`;

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    {applicationId ? (
      <ThunderIDProvider
        baseUrl={baseUrl}
        applicationId={applicationId}
        extensions={{
          components: {
            renderers: {
              SBI_ID: SbiCustomRenderer,
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
