import React, { useEffect, useState, useRef, useLayoutEffect } from 'react';
import ReactCountryFlag from 'react-country-flag';
import { useTranslation } from 'react-i18next';

const InputWithPrefix = (props) => {
  const [isDropdownOpen, setIsDropdownOpen] = useState(false);
  const [selectedCountry, setSelectedCountry] = useState(null);
  const [isValid, setIsValid] = useState(false);
  const [individualId, setIndividualId] = useState('');
  const { t, i18n } = useTranslation();
  const [prevLanguage, setPrevLanguage] = useState(i18n.language);
  const inputRef = useRef(null);

  const iso = require('iso-3166-1');
  const countries = iso.all();

  const toggleDropdown = () => {
    setIsDropdownOpen(!isDropdownOpen); // Toggle dropdown state
  };

  const countryFlagStyles = {
    width: '1em',
    height: '1em',
    objectFit: 'cover',
    borderRadius: '50%',
  };

  const dropdownDiv = document.getElementById(`${props.login}_login_dropdown`);
  document.addEventListener('click', (event) => {
    if (!dropdownDiv?.contains(event.target)) {
      setIsDropdownOpen(false);
    } else {
      setIsDropdownOpen(true);
    }
  });

  useLayoutEffect(() => {
    if (inputRef.current) {
      inputRef.current.focus();
    }
  }, [selectedCountry, i18n.language]);

  useEffect(() => {
    if (i18n.language === prevLanguage) {
      setIndividualId(null);
      setIsDropdownOpen(false);
      if (props.currentLoginID && props.currentLoginID.prefixes) {
        setSelectedCountry(props.currentLoginID.prefixes[0]);
        props.countryCode(props.currentLoginID.prefixes[0].value);
      }
    } else {
      setPrevLanguage(i18n.language);
    }
  }, [props.currentLoginID]);

  function getPropertiesForLoginID(loginID, label) {
    const { prefixes, maxLength: outerMaxLength, regex: outerRegex } = loginID;

    if (Array.isArray(prefixes) && prefixes.length > 0) {
      const prefix = prefixes.find((prefix) => prefix.label === label);
      if (prefix) {
        return {
          maxLength: prefix.maxLength || outerMaxLength || null,
          regex: prefix.regex || outerRegex || null,
        };
      }
    }

    return {
      maxLength: outerMaxLength || null,
      regex: outerRegex || null,
    };
  }

  const handleChange = (e) => {
    setIsValid(true);
    const idProperties = getPropertiesForLoginID(
      props.currentLoginID,
      e.target.name.split('_')[1]
    );
    const maxLength = idProperties.maxLength;
    const regex = idProperties.regex ? new RegExp(idProperties.regex) : null;
    const trimmedValue = e.target.value.trim();

    let newValue = trimmedValue;

    setIndividualId(newValue);
    props.individualId(newValue); // Update state with the visible valid value

    const isValidInput =
      (!maxLength && !regex) || // Case 1: No maxLength, no regex
      (maxLength && !regex && newValue.length <= parseInt(maxLength)) || // Case 2: maxLength only
      (!maxLength && regex && regex.test(newValue)) || // Case 3: regex only
      (maxLength &&
        regex &&
        newValue.length <= parseInt(maxLength) &&
        regex.test(newValue)); // Case 4: Both maxLength and regex

    props.isBtnDisabled(!isValidInput);
  };

  const handleBlur = (e) => {
    const idProperties = getPropertiesForLoginID(
      props.currentLoginID,
      e.target.name.split('_')[1]
    );
    const maxLength = idProperties.maxLength;
    const regex = idProperties.regex ? new RegExp(idProperties.regex) : null;
    setIsValid(
      (!maxLength || e.target.value.trim().length <= parseInt(maxLength)) &&
        (!regex || regex.test(e.target.value.trim()))
    );
  };

  const handleCountryChange = (option) => {
    setSelectedCountry(option);
    setIsDropdownOpen(false);
    props.isBtnDisabled(true);
    props.countryCode(option.value);
    if (selectedCountry.value !== option.value) {
      setIndividualId(null);
      props.isBtnDisabled(true);
    } else {
      props.isBtnDisabled(false);
    }
  };

  return (
    props.currentLoginID &&
    selectedCountry && (
      <>
        <label
          className="block mb-1 text-sm font-medium w-max mt-4"
          htmlFor={props.currentLoginID.id}
        >
          {props.currentLoginID.input_label}
        </label>
        <div
          className={`flex items-center border rounded-lg md:max-w-md w-full mt-2 relative overflow-visible ${
            !isValid && individualId?.length > 0 && 'border-[#FE6B6B]'
          }`}
        >
          <div
            className="relative inline-block text-left rounded-lg"
            id={`${props.login}_login_dropdown`}
          >
            <button
              className="flex flex-col bg-white p-2 text-gray-700 cursor-pointer rounded-lg w-max"
              onClick={
                typeof props.currentLoginID?.prefixes === 'object' &&
                toggleDropdown
              }
              id={`${props.login}_login_dropdown_button`}
              data-testid="prefix-dropdown-btn"
              type="button"
            >
              {typeof props.currentLoginID?.prefixes === 'object' ? (
                <div className="flex items-center justify-between w-full">
                  <div className="flex items-center">
                    <span className="mr-2 relative bottom-[2px]">
                      <ReactCountryFlag
                        countryCode={
                          countries.find(
                            (country) =>
                              country.alpha3 === selectedCountry.label
                          )?.alpha2
                        }
                        alt={selectedCountry.label}
                        svg
                        style={countryFlagStyles}
                      />
                    </span>
                    <span>
                      {selectedCountry.label} ({selectedCountry.value})
                    </span>
                  </div>
                  {props.currentLoginID?.prefixes?.length > 1 && (
                    <span className="ml-2 relative top-[1.5px]">
                      {/* Dropdown arrow */}
                      <img
                        src="/images/up_down_arrow_icon.svg"
                        alt="up_down_arrow_icon"
                      />
                    </span>
                  )}
                </div>
              ) : (
                <div className="flex items-center justify-between w-full">
                  <div className="flex items-center">
                    {props.currentLoginID?.prefixes}
                  </div>
                </div>
              )}
            </button>
          </div>

          {isDropdownOpen &&
            props.currentLoginID?.prefixes?.length > 1 &&
            typeof props.currentLoginID?.prefixes === 'object' && (
              <div className="absolute top-full left-0 rounded-md shadow-lg bg-white z-50 border border-gray-200 max-h-60 overflow-auto inset-auto mt-0 w-max">
                <div className="py-1 text-gray-700 w-full">
                  {props.currentLoginID?.prefixes.map((option) => (
                    <button
                      key={option.value}
                      id={option.label}
                      className={`flex items-center p-2 cursor-pointer hover:bg-gray-100 w-full pr-[1.65rem] ${
                        option.label === selectedCountry.label
                          ? 'bg-gray-100'
                          : ''
                      }`}
                      onClick={() => handleCountryChange(option)}
                      type="button"
                    >
                      <span className="mr-2 relative bottom-[2px]">
                        <ReactCountryFlag
                          countryCode={
                            countries.find(
                              (country) => country.alpha3 === option.label
                            )?.alpha2
                          }
                          svg
                          style={countryFlagStyles}
                        />
                      </span>
                      <span>
                        {option.label} ({option.value})
                      </span>
                    </button>
                  ))}
                </div>
              </div>
            )}
          <input
            ref={inputRef}
            type="text"
            placeholder={props.currentLoginID.input_placeholder}
            className={`w-full px-4 py-2 border border-l-1 border-b-0 border-r-0 border-t-0 outline-none rounded-tr-lg rounded-br-lg ${
              !isValid && individualId?.length > 0 && 'border-[#FE6B6B]'
            }`}
            onChange={handleChange}
            onBlur={handleBlur}
            id={props.login + '_' + selectedCountry.label}
            name={props.login + '_' + selectedCountry.label}
            value={individualId ?? ''}
          />
        </div>
        {!isValid && individualId?.length > 0 && (
          <small className="text-[#FE6B6B] font-medium flex items-center mt-1">
            <span>
              <img
                src="\images\error_icon.svg"
                alt="error_icon"
                className="mr-1"
                width="12px"
              />
            </span>
            <span>
              {`${t(`${props.i18nPrefix}.invalid_input`, {
                id: `${t(`${props.i18nPrefix}.${props.currentLoginID.id}`)}`,
              })}`}
            </span>
          </small>
        )}
      </>
    )
  );
};

export default InputWithPrefix;
