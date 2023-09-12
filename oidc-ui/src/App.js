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

function App() {
  const { i18n } = useTranslation();
  const [langOptions, setLangOptions] = useState([]);
  const [dir, setDir] = useState("");
  const [statusLoading, SetStatusLoading] = useState(states.LOADING);

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
        SetStatusLoading(states.LOADED)
      });
    } catch (error) {
      console.error("Failed to load rtl languages!");
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

    //4. default lang set in env_configs file as fallback language.
  };

  let el;

  switch (statusLoading) {
    case states.LOADING:
      el = <LoadingIndicator size="medium" message={"loading_msg"} />;
      break;
    case states.LOADED:
      el = (
        <div dir={dir} className="h-screen">
          <NavHeader langOptions={langOptions} />
          <BrowserRouter>
            <Routes>
              <Route path={process.env.PUBLIC_URL + "/"} element={<EsignetDetailsPage />} />
              <Route path={process.env.PUBLIC_URL + "/login"} element={<LoginPage />} />
              <Route path={process.env.PUBLIC_URL + "/authorize"} element={<AuthorizePage />} />
              <Route path={process.env.PUBLIC_URL + "/consent"} element={<ConsentPage />} />
            </Routes>
          </BrowserRouter>
        </div>
      );
      break;
  }

  return el;
}

export default App;
