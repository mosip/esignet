import "./App.css";
import { BrowserRouter, Routes, Route } from "react-router-dom";
import LoginPage from "./pages/Login";
import AuthorizePage from "./pages/Authorize";
import ConsentPage from "./pages/Consent";
import NavHeader from "./components/NavHeader";
import langConfigService from "./services/langConfigService";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import EsignetDetailsPage from "./pages/EsignetDetails";
import LoadingIndicator from "./common/LoadingIndicator";
import { LoadingStates as states } from "./constants/states";
import Footer from "./components/Footer";
import configService from "./services/configService";

const config = await configService();

function App() {
  const { i18n } = useTranslation();
  const [langOptions, setLangOptions] = useState([]);
  const [dir, setDir] = useState("");
  const [statusLoading, setStatusLoading] = useState(states.LOADING);

  const { t } = useTranslation();

  //Loading rtlLangs
  useEffect(() => {
    try {
      langConfigService.getLocaleConfiguration().then((response) => {
        let lookup = {};
        let supportedLanguages = response.languages_2Letters;
        let langData = [];
        for (let lang in supportedLanguages) {
          //check to avoid duplication language labels
          if (!(supportedLanguages[lang] in lookup)) {
            lookup[supportedLanguages[lang]] = 1;
            langData.push({
              label: supportedLanguages[lang],
              value: lang,
            });
          }
        }
        changeLanguage(response);
        setDir(response.rtlLanguages.includes(i18n.language) ? "rtl" : "ltr");

        //Gets fired when changeLanguage got called.
        i18n.on("languageChanged", function (lng) {
          setDir(response.rtlLanguages.includes(lng) ? "rtl" : "ltr");
        });
        setLangOptions(langData);
        setStatusLoading(states.LOADED);
      });
    } catch (error) {
      console.error("Failed to load rtl languages!");
    }

    window.onbeforeunload = function() {
      return true;
    }
  }, []);

  const changeLanguage = (loadLang) => {
    //Language detector priotity order: ['querystring', 'cookie', 'localStorage',
    //      'sessionStorage', 'navigator', 'htmlTag', 'path', 'subdomain'],

    //1. Check for ui locales param. Highest priority.
    //This will override the language detectors selected language
    let supportedLanguages = loadLang.languages_2Letters;
    let searchUrlParams = new URLSearchParams(window.location.search);
    let uiLocales = searchUrlParams.get("ui_locales");
    if (uiLocales) {
      let languages = uiLocales.split(" ");
      for (let idx in languages) {
        if (supportedLanguages[languages[idx]]) {
          i18n.changeLanguage(languages[idx]);
          return;
        }
      }

      // if language code not found in 2 letter codes, then check mapped language codes
      let langCodeMapping = loadLang.langCodeMapping;
      for (let idx in languages) {
        if (langCodeMapping[languages[idx]]) {
          i18n.changeLanguage(langCodeMapping[languages[idx]]);
          return;
        }
      }
    }


    //2. Check for cookie
    //Language detector will store and use cookie "i18nextLng"

    //3. Check for system locale
    //Language detector will check navigator and subdomain to select proper language

    //4. default lang set in env-config file as fallback language.
  };

  // set the minimum height of the section between navbar and the footer
  var sectionMinHeight  = (window.innerHeight - (document.getElementById("footer")?.offsetHeight + document.getElementById("navbar-header")?.offsetHeight)) + "px"

  // check if background logo is required or not,
  // create a div according to the config variable
  const backgroundLogoDiv =
    config["background_logo"] ? (
      <div className="flex justify-center m-10 lg:mt-20 mb:mt-0 lg:w-1/2 md:w-1/2 md:block sm:w-1/2 sm:block hidden w-5/6 mt-20 mb-10 md:mb-0">
        <img
          className="background-logo object-contain rtl:scale-x-[-1]"
          alt={t("header.backgroud_image_alt")}
        />
      </div>
    ) : (
      <>
        <img className="top_left_bg_logo hidden md:block" alt="top left background" />
        <img className="bottom_left_bg_logo hidden md:block" alt="bottom right background" />
      </>
    );

  let el;

  switch (statusLoading) {
    case states.LOADING:
      el = (
        <div className="h-screen flex justify-center content-center">
          <LoadingIndicator size="medium" />
        </div>
      );
      break;
    case states.LOADED:
      el = (
        <div dir={dir} className="h-screen">
          <NavHeader langOptions={langOptions} />
          <BrowserRouter>
            <div className="section-background" style={{minHeight: sectionMinHeight}}>
            <section className="login-text body-font pt-0 md:py-4">
              <div className="container justify-center flex mx-auto sm:flex-row flex-col min-h-0" style={window.screen.width >= 768 ? {minHeight: sectionMinHeight} : null}>
              {backgroundLogoDiv}
              <Routes>
                <Route path={process.env.PUBLIC_URL + "/"} element={<EsignetDetailsPage />} />
                <Route path={process.env.PUBLIC_URL + "/login"} element={<LoginPage />} />
                <Route path={process.env.PUBLIC_URL + "/authorize"} element={<AuthorizePage />} />
                <Route path={process.env.PUBLIC_URL + "/consent"} element={<ConsentPage />} />
              </Routes>
              </div>
            </section>
            </div>
          </BrowserRouter>
          <Footer />
        </div>
      );
      break;
  }

  return el;
}

export default App;
