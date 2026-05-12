import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { AsgardeoProvider } from "@asgardeo/react";
import App from './App';

// getting applicationId from query param to pass it to AsgardeoProvider
const applicationId = new URL(window.location.href).searchParams.get(
  'applicationId'
);

const baseUrl = import.meta.env.VITE_API_URL || 'https://localhost:8090';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    {applicationId ? (
      <AsgardeoProvider
        baseUrl={baseUrl}
        platform="AsgardeoV2"
        applicationId={applicationId}
      >
        <App />
      </AsgardeoProvider>
    ) : (
      <App />
    )}
  </StrictMode>,
);
