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

function App() {
  const { i18n } = useTranslation();
  const [langOptions, setLangOptions] = useState([]);
  const [dir, setDir] = useState("");

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

        setLangOptions(langData);
        setDir(response.rtlLanguages.includes(i18n.language) ? "rtl" : "ltr");

        //Gets fired when changeLanguage got called.
        i18n.on("languageChanged", function (lng) {
          setDir(response.rtlLanguages.includes(lng) ? "rtl" : "ltr");
        });
      });
    } catch (error) {
      console.error("Failed to load rtl languages!");
    }
  }, []);

  return (
    <div dir={dir} className="h-screen">
      <NavHeader langOptions={langOptions} />
      <BrowserRouter>
        <Routes>
          <Route path="/" element={<EsignetDetailsPage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/authorize" element={<AuthorizePage />} />
          <Route path="/consent" element={<ConsentPage />} />
        </Routes>
      </BrowserRouter>
    </div>
  );
}

export default App;
