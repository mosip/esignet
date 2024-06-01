import { Suspense, useEffect } from "react";
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
} from "../constants/routes";
import configService from "../services/configService";
import ClaimDetails from "../components/ClaimDetails";

const config = await configService();

const WithSuspense = ({ children }) => (
  <Suspense fallback={<div className="h-screen w-screen bg-neutral-100"></div>}>
    {children}
  </Suspense>
);

export const AppRouter = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { t } = useTranslation();

  useEffect(() => {
    setupResponseInterceptor(navigate);
  }, [navigate]);

  const checkRoute = (currentRoute) =>
    [LOGIN, AUTHORIZE, CONSENT].includes(currentRoute);

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
    { route: PAGE_NOT_FOUND, component: <PageNotFoundPage /> },
    { route: "*", component: <PageNotFoundPage /> },
  ];

  return (
    <WithSuspense>
      <div className="section-background">
        <section className="login-text body-font pt-0 md:py-4">
          <div className="container justify-center flex mx-auto sm:flex-row flex-col">
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
