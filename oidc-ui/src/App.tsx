import { useEffect, useState } from 'react';
import { BrowserRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import './App.css';
import NavHeader from './components/NavHeader';
import Footer from './components/Footer';
import AppRouter from './routes/AppRouter';
import LoadingIndicator from './components/LoadingIndicator';
import { initializeCSSVariables } from './services/css-variable.service';
import { LoadingStates } from './constants/states';
import type { LoadingState } from './constants/states';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: (failureCount, error) => {
        const status = (error as { status?: number })?.status;
        if (status && status >= 400 && status < 500) return false;
        return failureCount < 3;
      },
    },
  },
});

export default function App() {
  const [status, setStatus] = useState<LoadingState>(LoadingStates.LOADING);

  useEffect(() => {
    initializeCSSVariables();
    setStatus(LoadingStates.LOADED);
  }, []);

  if (status === LoadingStates.LOADING) {
    return (
      <div className="align-loading-center">
        <LoadingIndicator size="large" />
      </div>
    );
  }

  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <div className="flex flex-col min-h-screen">
          <NavHeader />
          <main className="flex-1">
            <AppRouter />
          </main>
          <Footer />
        </div>
      </BrowserRouter>
    </QueryClientProvider>
  );
}
