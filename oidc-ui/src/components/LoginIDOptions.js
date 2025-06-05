import React, { useState, useEffect } from "react";
import { useTranslation } from "react-i18next";
import openIDConnectService from "../services/openIDConnectService";
import { configurationKeys } from "../constants/clientConstants";
import { decodeHash } from "../helpers/utils";

const LoginIDOptions = (props) => {
  const [selectedOption, setSelectedOption] = useState();
  const { t, i18n } = useTranslation("translation", {
    keyPrefix: "loginOptions",
  });

  // Parsing the current URL into a URL object
  const urlObj = new URL(window.location.href);

  // Extracting the value of the 'state' and 'nonce' parameter from the URL
  const state = urlObj.searchParams.get("state");
  const nonce = urlObj.searchParams.get("nonce");

  // Extracting the hash part of the URL (excluding the # character)
  const code = urlObj.hash.substring(1);

  // Decoding the Base64-encoded string (excluding the first character)
  const decodedBase64 = decodeHash(code);

  // Creating an instance of the openIDConnectService with decodedBase64, nonce, and state parameters
  const oidcService = new openIDConnectService(
    JSON.parse(decodedBase64),
    nonce,
    state
  );

  let loginIDs = oidcService.getEsignetConfiguration(
    configurationKeys.loginIdOptions
  );

  if (!loginIDs || loginIDs.length === 0) {
    loginIDs = [
      {
        id: "vid",
        svg: "vid_icon",
        prefixes: "",
        postfix: "",
        maxLength: "",
        regex: "",
      },
    ];
  }

  const [iconsMap, setIconsMap] = useState({}); // To store preloaded SVGs
  const fetchSvg = async (path) => {
    try {
      const response = await fetch(`/images/${path}.svg`);
      if (!response.ok) {
        throw new Error("Failed to fetch SVG");
      }
      let svgText = await response.text();

      // Ensure that all stroke attributes are set to "currentColor"
      svgText = svgText.replace(/stroke="[^"]*"/g, 'stroke="currentColor"');

      return svgText;
    } catch (error) {
      return ""; // Return empty string if error occurs
    }
  };

  useEffect(() => {
    const preloadIcons = async () => {
      const svgPromises = loginIDs.map(async (option) => {
        const svgContent = await fetchSvg(option.svg);
        return { id: option.id, svgContent };
      });

      const svgResults = await Promise.all(svgPromises);
      const svgMap = svgResults.reduce((acc, { id, svgContent }) => {
        acc[id] = svgContent;
        return acc;
      }, {});

      setIconsMap(svgMap); // Store preloaded SVGs
      setSelectedOption(options[0]);
    };

    preloadIcons();
  }, []);

  // Transform the array into the desired structure
  const options = loginIDs.map((option) => ({
    ...option, // Spread existing properties like id, prefix, postfix, etc.
    svg: (
      <div
        dangerouslySetInnerHTML={{ __html: iconsMap[option.id] || "" }}
      ></div>
    ), // Modify the svg
    input_label: t(`input.label.${option.id}`), // Add input_label
    input_placeholder: t(`input.placeholder.${option.id}`), // Add input_placeholder
  }));

  useEffect(() => {
    setSelectedOption((prevState) => {
      if (!prevState) return null; // No selection to update

      return {
        ...prevState,
        input_label: t(`input.label.${prevState.id}`),
        input_placeholder: t(`input.placeholder.${prevState.id}`),
      };
    });
  }, [i18n.language]);

  props.currentLoginID(selectedOption);

  return (
    selectedOption && (
      <div className="p-0">
        <div className="grid grid-cols-2 gap-3">
          {options.map((option, index) => (
            <button
              key={option.id}
              id={option.id}
              onClick={() => setSelectedOption(option)}
              className={`flex items-center justify-start w-full p-2 rounded-lg border-2 text-left login_id text-[#667085]
                ${selectedOption.id === option.id ? "selected_login_id" : null}
                ${
                  options.length % 2 !== 0 &&
                  index === options.length - 1 &&
                  options.length > 1
                    ? "col-span-2 mx-auto w-1/2"
                    : options.length === 1
                    ? "hidden"
                    : ""
                }`}
            >
              <span className="mr-2 relative top-[1px]">{option.svg}</span>
              <span>{t(`buttons.${option.id}`)}</span>
            </button>
          ))}
        </div>
      </div>
    )
  );
};

export default LoginIDOptions;
