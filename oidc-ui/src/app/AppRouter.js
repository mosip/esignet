import { Suspense, useEffect, useState } from 'react';
import { Route, Routes, useNavigate, useLocation } from 'react-router-dom';
import {
  LoginPage,
  AuthorizePage,
  ConsentPage,
  EsignetDetailsPage,
  SomethingWrongPage,
  PageNotFoundPage,
} from '../pages';
import { setupResponseInterceptor } from '../services/api.service';
import { useTranslation } from 'react-i18next';
import {
  AUTHORIZE,
  CONSENT,
  LOGIN,
  PAGE_NOT_FOUND,
  SOMETHING_WENT_WRONG,
  ESIGNET_DETAIL,
  CLAIM_DETAIL,
  NETWORK_ERROR,
} from '../constants/routes';
import configService from '../services/configService';
import ClaimDetails from '../components/ClaimDetails';
import NetworkError from '../pages/NetworkError';
import { Detector } from 'react-detect-offline';
import { getPollingConfig } from '../helpers/utils';
import LoadingIndicator from '../common/LoadingIndicator';

const WithSuspense = ({ children }) => (
  <Suspense fallback={<div className="h-screen w-screen bg-neutral-100"></div>}>
    {children}
  </Suspense>
);

export const AppRouter = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { t } = useTranslation();
  const [currentUrl, setCurrentUrl] = useState(window.location.href);
  const [config, setConfig] = useState(null); // State to store config
  const [isLoadingConfig, setIsLoadingConfig] = useState(true);
  const pollingConfig = getPollingConfig();

  useEffect(() => {
    const fetchConfig = async () => {
      try {
        const appConfig = await configService();
        setConfig(appConfig);
      } catch (error) {
        console.error('Failed to fetch config:', error);
        // Consider navigating to an error page or showing an error message
      } finally {
        setIsLoadingConfig(false); // Always set to false after fetch attempt
      }
    };
    fetchConfig();
  }, []); // Run once on component mount

  useEffect(() => {
    if (location.pathname !== NETWORK_ERROR) {
      setCurrentUrl(window.location.href);
    }
  }, [location.pathname]);

  useEffect(() => {
    setupResponseInterceptor(navigate);
  }, [navigate]);

  if (window.location.pathname === CLAIM_DETAIL) {
    document.body.style.overflow = 'hidden';
  } else {
    document.body.style.overflow = 'unset';
  }

  const checkRoute = (currentRoute) =>
    [LOGIN, AUTHORIZE, CONSENT, NETWORK_ERROR].includes(currentRoute);

  // Show a loading state until config is fetched
  if (isLoadingConfig) {
    return (
      <div className="h-screen flex justify-center content-center">
        <LoadingIndicator
          size="medium"
          message={'loading_msg'}
          className="align-loading-center"
        />
      </div>
    );
  }

  // Now that config is guaranteed to be loaded (or null if fetching failed but isLoadingConfig is false),
  // we can safely access its properties, adding null checks where appropriate.
  const backgroundLogoDiv = checkRoute(location.pathname) ? (
    config && config['background_logo'] ? (
      <div className="flex justify-center m-10 lg:mt-20 mb:mt-0 lg:w-1/2 md:w-1/2 md:block sm:w-1/2 sm:block hidden w-5/6 mt-20 mb-10 md:mb-0">
        <img
          className="background-logo object-contain rtl:scale-x-[-1]"
          alt={t('header.backgroud_image_alt')}
        />
      </div>
    ) : (
      <>
        <img
          className="top_left_bg_logo hidden md:block"
          alt="top left background"
        />
        <img
          className="bottom_left_bg_logo hidden md:block"
          alt="bottom left background"
        />
        <img
          className="top_right_bg_logo hidden md:block"
          alt="top right background"
        />
        <img
          className="bottom_right_bg_logo hidden md:block"
          alt="bottom right background"
        />
      </>
    )
  ) : (
    <></>
  );

  // array of all routes
  const esignetRoutes = [
    { route: ESIGNET_DETAIL, component: <EsignetDetailsPage /> },
    { route: LOGIN, component: <LoginPage /> },
    { route: AUTHORIZE, component: <AuthorizePage /> },
    { route: CONSENT, component: <ConsentPage /> },
    { route: CLAIM_DETAIL, component: <ClaimDetails /> },
    { route: SOMETHING_WENT_WRONG, component: <SomethingWrongPage /> },
    { route: NETWORK_ERROR, component: <NetworkError /> },
    { route: PAGE_NOT_FOUND, component: <PageNotFoundPage /> },
    { route: '*', component: <PageNotFoundPage /> },
  ];

  return (
    <WithSuspense>
      <div className="section-background">
        <section className="login-text body-font pt-0 md:py-4">
          <div className="container justify-center flex mx-auto sm:flex-row flex-col">
            <Detector
              polling={{
                url: pollingConfig.url, // Set the polling URL dynamically
                interval: pollingConfig.interval, // Optional: Check every 10 seconds (default is 10000ms)
                timeout: pollingConfig.timeout, // Optional: Timeout after 5 seconds (default is 5000ms)
                enabled: pollingConfig.enabled, // Optional: Enable or disable polling (default is true)
              }}
              render={({ online }) => {
                if (!online) {
                  navigate(NETWORK_ERROR, {
                    state: {
                      path: currentUrl,
                    },
                  });
                }
              }}
            />
            {backgroundLogoDiv}
            <Routes>
              {esignetRoutes.map((route) => (
                <Route
                  exact
                  key={route.route}
                  path={process.env.PUBLIC_URL + route.route}
                  element={route.component}
                />
              ))}
            </Routes>
          </div>
        </section>
      </div>
    </WithSuspense>
  );
};
