import "./App.css";
import { BrowserRouter, Routes, Route } from "react-router-dom";
import LoginPage from "./pages/Login";
import AuthorizePage from "./pages/Authorize";
import ConsentPage from "./pages/Consent";
import NavHeader from "./components/NavHeader";
import IdpDetailsPage from "./pages/IdpDetails";
import langConfigService from "./services/langConfigService";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";

function App() {
  const { i18n } = useTranslation();

  const [rtlLangs, setRtlLangs] = useState([]);
  const [langOptions, setLangOptions] = useState([]);
  const [dir, setDir] = useState("");

  //Loading rtlLangs
  useEffect(() => {
    try {
      langConfigService.getLocaleConfiguration().then((response) => {
        let lookup = {};
        let supportedLanguages = response.languages;
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
        setLangOptions(langData);
        setRtlLangs(response.rtlLanguages);
        setDir(response.rtlLanguages.includes(i18n.language) ? "rtl" : "ltr")
      });
    } catch (error) {
      console.error("Failed to load rtl languages!");
    }
  }, []);

  //Gets fired when changeLanguage got called.
  i18n.on('languageChanged', function (lng) {
    setDir(rtlLangs.includes(lng) ? "rtl" : "ltr")
  })

  return (
    <div dir={dir}>
      <NavHeader langOptions={langOptions} />
      <BrowserRouter>
        <Routes>
          <Route path="/" element={<IdpDetailsPage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/authorize" element={<AuthorizePage />} />
          <Route path="/consent" element={<ConsentPage />} />
        </Routes>
      </BrowserRouter>
    </div>
  );
}

export default App;
