import { Suspense, useEffect, useState } from "react";
import { Route, Routes, useNavigate, useLocation } from "react-router-dom";
import {
  LoginPage,
  AuthorizePage,
  ConsentPage,
  EsignetDetailsPage,
  SomethingWrongPage,
  PageNotFoundPage,
} from "../pages";
import { setupResponseInterceptor } from "../services/api.service";
import { useTranslation } from "react-i18next";
import {
  AUTHORIZE,
  CONSENT,
  LOGIN,
  PAGE_NOT_FOUND,
  SOMETHING_WENT_WRONG,
  ESIGNET_DETAIL,
  CLAIM_DETAIL,
  NETWORK_ERROR,
} from "../constants/routes";
import configService from "../services/configService";
import ClaimDetails from "../components/ClaimDetails";
import NetworkError from "../pages/NetworkError";
import { Detector } from "react-detect-offline";

const config = await configService();

const WithSuspense = ({ children }) => (
  <Suspense fallback={<div className="h-screen w-screen bg-neutral-100"></div>}>
    {children}
  </Suspense>
);

const POLLING_BASE_URL =
  process.env.NODE_ENV === "development"
    ? process.env.REACT_APP_ESIGNET_API_URL
    : window.origin + process.env.REACT_APP_ESIGNET_API_URL;

export const AppRouter = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { t } = useTranslation();
  const [currentUrl, setCurrentUrl] = useState(window.location.href);
  const pollingUrl = POLLING_BASE_URL + "/actuator/health";

  useEffect(() => {
    if (location.pathname !== NETWORK_ERROR) {
      setCurrentUrl(window.location.href);
    }
  }, [location.pathname]);

  useEffect(() => {
    setupResponseInterceptor(navigate);
  }, [navigate]);

  if (window.location.pathname === CLAIM_DETAIL) {
    document.body.style.overflow = "hidden";
  } else {
    document.body.style.overflow = "unset";
  }

  const checkRoute = (currentRoute) =>
    [LOGIN, AUTHORIZE, CONSENT, NETWORK_ERROR].includes(currentRoute);

  // checking the pathname if login, consent, authorize
  // is present then only show the background
  // check if background logo is required or not,
  // create a div according to the config variable
  const backgroundLogoDiv = checkRoute(location.pathname) ? (
    config["background_logo"] ? (
      <div className="flex justify-center m-10 lg:mt-20 mb:mt-0 lg:w-1/2 md:w-1/2 md:block sm:w-1/2 sm:block hidden w-5/6 mt-20 mb-10 md:mb-0">
        <img
          className="background-logo object-contain rtl:scale-x-[-1]"
          alt={t("header.backgroud_image_alt")}
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
    { route: "*", component: <PageNotFoundPage /> },
  ];

  return (
    <WithSuspense>
      <div className="section-background">
        <section className="login-text body-font pt-0 md:py-4">
          <div className="container justify-center flex mx-auto sm:flex-row flex-col">
            <Detector
              polling={{
                  url: pollingUrl, // Set the polling URL dynamically
                  interval: 10000, // Optional: Check every 5 seconds (default is 5000ms)
                  timeout: 5000,  // Optional: Timeout after 3 seconds (default is 5000ms)
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
              {/* <Route component={PageNotFoundPage} /> */}
            </Routes>
          </div>
        </section>
      </div>
    </WithSuspense>
  );
};
