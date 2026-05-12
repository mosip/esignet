import { useEffect, useState } from 'react';
import { BrowserRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import './i18n';
import './App.css';
import NavHeader from './components/NavHeader';
import Footer from './components/Footer';
import AppRouter from './routes/AppRouter';
import LoadingIndicator from './components/LoadingIndicator';
import { initializeCSSVariables } from './services/css-variable.service';
import { getLocaleConfiguration } from './services/lang-config.service';
import { LoadingStates } from './constants/states';
import type { LoadingState } from './constants/states';
import type { LangOption } from './types';

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
  const { i18n } = useTranslation();
  const [status, setStatus] = useState<LoadingState>(LoadingStates.LOADING);
  const [langOptions, setLangOptions] = useState<LangOption[]>([]);
  const [dir, setDir] = useState<'ltr' | 'rtl'>('ltr');

  useEffect(() => {
    initializeCSSVariables();

    const init = async () => {
      try {
        const localeConfig = await getLocaleConfiguration();

        // Build language options
        const options: LangOption[] = Object.entries(
          localeConfig.languages_2Letters,
        ).map(([code, name]) => ({
          value: code,
          label: name,
        }));
        setLangOptions(options);

        // Detect initial language from query params
        const urlParams = new URLSearchParams(window.location.search);
        const uiLocales = urlParams.get('ui_locales');
        if (uiLocales) {
          const langCode =
            localeConfig.langCodeMapping[uiLocales] ?? uiLocales;
          await i18n.changeLanguage(langCode);
        }

        // Set RTL direction
        const rtlLangs = localeConfig.rtlLanguages ?? [];
        setDir(rtlLangs.includes(i18n.language) ? 'rtl' : 'ltr');

        setStatus(LoadingStates.LOADED);
      } catch {
        setStatus(LoadingStates.ERROR);
      }
    };

    init();
  }, [i18n]);

  // Update dir on language change
  useEffect(() => {
    const onLanguageChanged = async () => {
      try {
        const config = await getLocaleConfiguration();
        const rtlLangs = config.rtlLanguages ?? [];
        setDir(rtlLangs.includes(i18n.language) ? 'rtl' : 'ltr');
      } catch {
        // Keep current direction on failure
      }
    };

    i18n.on('languageChanged', onLanguageChanged);
    return () => {
      i18n.off('languageChanged', onLanguageChanged);
    };
  }, [i18n]);

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
        <div dir={dir} className="flex flex-col min-h-screen">
          <NavHeader langOptions={langOptions} />
          <main className="flex-1">
            <AppRouter />
          </main>
          <Footer />
        </div>
      </BrowserRouter>
    </QueryClientProvider>
  );
}
