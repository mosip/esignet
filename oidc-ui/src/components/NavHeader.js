import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import Select from "react-select";

export default function NavHeader({ langOptions, i18nKeyPrefix = "header" }) {
  const { t, i18n } = useTranslation("translation", {
    keyPrefix: i18nKeyPrefix,
  });
  const [selectedLang, setSelectedLang] = useState();

  const brandLogoUrl =
    process.env.REACT_APP_AUTH_BRAND_LOGO_URL ?? "images/veridonia_logo.png";

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
      const defaultLanguageCode = window["envConfigs"].defaultLang;

      // Find the language option that matches the extracted language code
      const defaultLang = langOptions.find(
        (option) => option.value === defaultLanguageCode
      );
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
    <nav className="bg-white border-gray-500 shadow px-2 sm:px-4 py-2">
      <div className="flex justify-between">
        <div className="ltr:sm:ml-8 rtl:sm:mr-8 ltr:ml-1 rtl:mr-1">
          <img className="brand-logo" />
        </div>
        <div className="flex rtl:sm:ml-8 ltr:sm:mr-8 rtl:ml-1 ltr:mr-1">
          <div className="mx-2 rtl:scale-x-[-1]">
            <svg
              xmlns="http://www.w3.org/2000/svg"
              width="39"
              height="39"
              viewBox="0 0 39 39"
            >
              <g
                id="language_icon"
                data-name="language icon"
                transform="translate(-1211.5 -10.5)"
              >
                <path
                  id="Path_155385"
                  data-name="Path 155385"
                  d="M19,0A19,19,0,1,1,0,19,19,19,0,0,1,19,0Z"
                  transform="translate(1212 11)"
                  stroke="rgba(0,0,0,0)"
                  strokeWidth="1"
                  className="language-icon-bg-color"
                />
                <path
                  id="Path_155386"
                  data-name="Path 155386"
                  d="M298.862,206.5h-9.284A2.582,2.582,0,0,0,287,209.079v6.189a2.582,2.582,0,0,0,2.579,2.579h5.2l2.246,2.245a.774.774,0,0,0,1.32-.547v-1.7h.516a2.582,2.582,0,0,0,2.579-2.579v-6.189a2.582,2.582,0,0,0-2.579-2.579Zm-2.063,4.9h-.288a4.63,4.63,0,0,1-1.372,2.8,3.585,3.585,0,0,0,1.4.291.532.532,0,0,1,.534.516.5.5,0,0,1-.5.516,4.708,4.708,0,0,1-2.347-.633,4.606,4.606,0,0,1-2.331.633.531.531,0,0,1-.534-.516.5.5,0,0,1,.5-.516,3.681,3.681,0,0,0,1.45-.3,4.581,4.581,0,0,1-.967-1.327.516.516,0,1,1,.932-.442,3.6,3.6,0,0,0,.94,1.213,3.592,3.592,0,0,0,1.251-2.243h-3.827a.516.516,0,1,1,0-1.032H293.7v-1.032a.516.516,0,0,1,1.032,0v1.032H296.8a.516.516,0,0,1,0,1.032Z"
                  transform="translate(941.304 -179.827)"
                  className="language-icon-color"
                />
                <path
                  id="Path_155388"
                  data-name="Path 155388"
                  d="M130.862,91h-9.284A2.582,2.582,0,0,0,119,93.578v6.189a2.582,2.582,0,0,0,2.579,2.579h.516v1.7a.773.773,0,0,0,1.32.547l2.246-2.245h.56V99.251a3.585,3.585,0,0,1,.152-1.032h-1.461l-.8,1.76a.516.516,0,1,1-.939-.427l2.579-5.673a.516.516,0,0,1,.939,0l1.092,2.4a3.59,3.59,0,0,1,2.049-.641h3.61V93.577A2.582,2.582,0,0,0,130.863,91Z"
                  transform="translate(1101.051 -69.999)"
                  className="language-icon-color"
                />
              </g>
            </svg>
          </div>
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
