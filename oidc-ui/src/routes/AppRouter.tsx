import { Suspense, lazy, useEffect, useState } from "react";
import { Routes, Route, useLocation, useNavigate } from "react-router-dom";
import LoadingIndicator from "../components/LoadingIndicator";
import {
  LOGIN,
  ESIGNET_DETAIL,
  SOMETHING_WENT_WRONG,
  NETWORK_ERROR,
} from "../constants/routes";
import { fetchThemeConfig } from "../services/config.service";
import { getPollingConfig } from "../utils/parsing";
import type { ThemeConfig } from "../types";
import { Detector } from "react-detect-offline";

const EsignetDetailsPage = lazy(() => import("../pages/EsignetDetailsPage"));
const LoginPage = lazy(() => import("../pages/LoginPage"));
const SomethingWrongPage = lazy(() => import("../pages/SomethingWrongPage"));
const NetworkErrorPage = lazy(() => import("../pages/NetworkErrorPage"));
const PageNotFoundPage = lazy(() => import("../pages/PageNotFoundPage"));

export default function AppRouter() {
  const [config, setConfig] = useState<ThemeConfig | null>(null);
  const [isLoadingConfig, setIsLoadingConfig] = useState(true);
  const location = useLocation();
  const navigate = useNavigate();
  const pollingConfig = getPollingConfig();

  useEffect(() => {
    const fetchConfig = async () => {
      try {
        const appConfig = await fetchThemeConfig();
        setConfig(appConfig);
      } catch (error) {
        console.error("Failed to fetch config:", error);
        // Consider navigating to an error page or showing an error message
      } finally {
        setIsLoadingConfig(false); // Always set to false after fetch attempt
      }
    };
    fetchConfig();
  }, []); // Run once on component mount

  const checkRoute = (currentRoute: string) =>
    [LOGIN, NETWORK_ERROR].includes(currentRoute);

  // Show a loading state until config is fetched
  if (isLoadingConfig) {
    return (
      <div className="h-screen flex justify-center content-center">
        <LoadingIndicator
          size="medium"
          message={"loading_msg"}
          className="align-loading-center"
        />
      </div>
    );
  }

  // Now that config is guaranteed to be loaded (or null if fetching failed but isLoadingConfig is false),
  // we can safely access its properties, adding null checks where appropriate.
  const backgroundLogoDiv = checkRoute(location.pathname) ? (
    config && config["background_logo"] ? (
      <div className="flex justify-center m-10 lg:mt-20 md:mt-0 lg:w-1/2 md:w-1/2 md:block sm:w-1/2 sm:block hidden w-5/6 mt-20 mb-10 md:mb-0">
        <img
          className="background-logo object-contain rtl:scale-x-[-1]"
          alt="background"
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
  return (
    <Suspense
      fallback={
        <div className="align-loading-center">
          <LoadingIndicator size="large" />
        </div>
      }
    >
      <div className="section-background">
        <section className="login-text body-font pt-0 md:py-4">
          <div className="container justify-center flex mx-auto sm:flex-row flex-col">
            <Detector
              polling={{
                url: pollingConfig.url,
                interval: pollingConfig.interval,
                timeout: pollingConfig.timeout,
              }}
              render={({ online }) => {
                if (!online && location.pathname !== NETWORK_ERROR) {
                  navigate(NETWORK_ERROR, {
                    state: { path: location.pathname },
                  });
                }
                return null;
              }}
            />
            {backgroundLogoDiv}

            <Routes>
              <Route path={ESIGNET_DETAIL} element={<EsignetDetailsPage />} />
              <Route path={LOGIN} element={<LoginPage />} />
              <Route
                path={SOMETHING_WENT_WRONG}
                element={<SomethingWrongPage />}
              />
              <Route path={NETWORK_ERROR} element={<NetworkErrorPage />} />
              <Route path="*" element={<PageNotFoundPage />} />
            </Routes>
          </div>
        </section>
      </div>
    </Suspense>
  );
}
