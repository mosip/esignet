import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import Select from "react-select";

export default function NavHeader({ langOptions, i18nKeyPrefix = "header" }) {
  const { t, i18n } = useTranslation("translation", { keyPrefix: i18nKeyPrefix });
  const [selectedLang, setSelectedLang] = useState();

  const changeLanguageHandler = (e) => {
    i18n.changeLanguage(e.value);
  };

  const customStyles = {
    control: (base) => ({
      ...base,
      border: 0,
    }),
  };

  useEffect(() => {
    if (!langOptions || langOptions.length === 0) {
      return;
    }

    let lang = langOptions.find((option) => {
      return option.value === i18n.language;
    });

    if (lang == null) {
      const defaultLanguageCode = window._env_.DEFAULT_LANG;

      // Find the language option that matches the extracted language code
      const defaultLang = langOptions.find((option) => option.value === defaultLanguageCode);
      setSelectedLang(defaultLang);
    } else {
      setSelectedLang(lang);
    }

    //Gets fired when changeLanguage got called.
    i18n.on("languageChanged", function (lng) {
      let language = langOptions.find((option) => {
        return option.value === lng;
      });
      setSelectedLang(language);
    });
  }, [langOptions]);

  return (
    <nav className="bg-white border-gray-500 shadow px-2 sm:px-4 py-2" id="navbar-header">
      <div className="flex grid justify-items-end">
        <div className="flex">
          <img src="images/language_icon.png" className="mx-2 rtl:scale-x-[-1]" alt={t("language")} />
          <Select
            styles={customStyles}
            isSearchable={false}
            className="appearance-none"
            value={selectedLang}
            options={langOptions}
            onChange={changeLanguageHandler}
            id="language_selection"
          />
        </div>
      </div>
    </nav>
  );
}
