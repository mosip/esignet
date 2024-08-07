import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import Select from "react-select";
import configService from "../services/configService";
import * as DropdownMenu from "@radix-ui/react-dropdown-menu";
import openIDConnectService from "../services/openIDConnectService";
import authService from "../services/authService";
import { Buffer } from "buffer";

const config = await configService();

export default function NavHeader({ langOptions, i18nKeyPrefix = "header" }) {
  const { t, i18n } = useTranslation("translation", {
    keyPrefix: i18nKeyPrefix,
  });
  const [selectedLang, setSelectedLang] = useState();
  const authServices = new authService(openIDConnectService);
  const authorizeQueryParam = "authorize_query_param";
  const ui_locales = "ui_locales";

  const changeLanguageHandler = (e) => {
    i18n.changeLanguage(e.value);
  };

  const customStyles = {
    control: (base) => ({
      ...base,
      border: 0,
    }),
    ...(config["remove_language_indicator_pipe"] && {
      valueContainer: (base) => ({
        ...base,
        padding: 0,
      }),
      indicatorSeparator: (base) => ({
        ...base,
        display: "none",
      }),
      dropdownIndicator: (base) => ({
        ...base,
        "padding-left": 0,
        color: "#140701",
      }),
      menu: (base) => ({
        ...base,
        "min-width": "100px",
      }),
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

      // Setting up the current i18n language in the URL on every language change.

      // Decode the authorize query param
      const decodedBase64 = Buffer.from(
        authServices.getAuthorizeQueryParam(),
        "base64"
      ).toString();

      var urlSearchParams = new URLSearchParams(decodedBase64);

      // Convert the decoded string to JSON
      var jsonObject = {};
      urlSearchParams.forEach(function (value, key) {
        jsonObject[key] = value;

        // Assign the current i18n language to the ui_locales
        if (key === ui_locales) {
          jsonObject[key] = language.value;
        }
      });

      // Convert the JSON back to decoded string
      Object.entries(jsonObject).forEach(([key, value]) => {
        urlSearchParams.set(key, value);
      });

      // Encode the string
      var encodedString = urlSearchParams.toString();

      const encodedBase64 = Buffer.from(encodedString).toString("base64");

      // Remove the old authorizeQueryParam from the local storage
      localStorage.removeItem(authorizeQueryParam);

      // Insert the new authorizeQueryParam to the local storage
      localStorage.setItem(authorizeQueryParam, encodedBase64);
    });
  }, [langOptions]);

  var dropdownItemClass =
    "group text-[14px] leading-none flex items-center relative select-none outline-none data-[disabled]:pointer-events-none hover:font-bold cursor-pointer py-2";

  var borderBottomClass = "border-b-[1px]";

  return (
    <nav
      className="bg-white border-gray-500 md:px-[4rem] py-2 px-[0.5rem] navbar-header"
      id="navbar-header"
    >
      <div className="flex h-full items-center justify-between">
        <div className="ltr:sm:ml-8 rtl:sm:mr-8 ltr:ml-1 rtl:mr-1">
          <img className="brand-logo" alt="brand_logo" />
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
          {config["outline_dropdown"] ? (
            <Select
              styles={customStyles}
              isSearchable={false}
              className="appearance-none"
              value={selectedLang}
              options={langOptions}
              onChange={changeLanguageHandler}
              id="language_selection"
            />
          ) : (
            <DropdownMenu.Root>
              <DropdownMenu.Trigger asChild>
                <span
                  className="inline-flex items-center justify-center bg-white outline-none hover:cursor-pointer text-[14px]"
                  aria-label="Customise options"
                  id="language_selection"
                >
                  {selectedLang?.label}
                  <img
                    src="images/chevron_down.svg"
                    alt="chevron down"
                    className="mx-1 relative top-[1px]"
                  />
                </span>
              </DropdownMenu.Trigger>
              <DropdownMenu.Portal>
                <DropdownMenu.Content
                  className="min-w-[220px] bg-white rounded-md shadow-md will-change-[opacity,transform] data-[side=top]:animate-slideDownAndFade data-[side=right]:animate-slideLeftAndFade data-[side=bottom]:animate-slideUpAndFade data-[side=left]:animate-slideRightAndFade px-3 py-2 border border-[#BCBCBC] outline-0 relative top-[-0.5rem]"
                  sideOffset={5}
                >
                  {langOptions.map((key, idx) => (
                    <DropdownMenu.Item
                      id={key.value + idx}
                      key={key.value}
                      className={
                        i18n.language === key.value
                          ? langOptions.length - 1 !== idx
                            ? `font-bold ${dropdownItemClass} ${borderBottomClass}`
                            : `font-bold ${dropdownItemClass}`
                          : langOptions.length - 1 !== idx
                          ? `${dropdownItemClass} ${borderBottomClass}`
                          : `${dropdownItemClass}`
                      }
                      onSelect={() => changeLanguageHandler(key)}
                    >
                      {key.label}
                      <div className="ml-auto">
                        {i18n.language === key.value && (
                          <svg
                            xmlns="http://www.w3.org/2000/svg"
                            width="16"
                            height="16"
                            viewBox="0 0 24 24"
                            fill="none"
                            strokeWidth="2"
                            strokeLinecap="round"
                            strokeLinejoin="round"
                            className="lucide lucide-check relative top-[1px] checkIcon"
                          >
                            <path d="M20 6 9 17l-5-5" />
                          </svg>
                        )}
                      </div>
                    </DropdownMenu.Item>
                  ))}
                  <DropdownMenu.Arrow asChild>
                    <svg
                      xmlns="http://www.w3.org/2000/svg"
                      viewBox="0 0 30 10"
                      stroke="#BCBCBC"
                      height={7}
                    >
                      <polygon points="0,0 30,0 15,10" fill="#fff" />
                      <line
                        x1="1"
                        y1="0"
                        x2="29"
                        y2="0"
                        stroke="#fff"
                        stroke-width="1"
                      />
                    </svg>
                  </DropdownMenu.Arrow>
                </DropdownMenu.Content>
              </DropdownMenu.Portal>
            </DropdownMenu.Root>
          )}
        </div>
      </div>
    </nav>
  );
}
