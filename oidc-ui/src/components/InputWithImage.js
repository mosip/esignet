import { useState, useRef } from "react";
import { useTranslation } from "react-i18next";
import PopoverContainer from "../common/Popover";

const fixedInputClass =
  "rounded-md bg-white shadow-lg appearance-none block w-full px-3.5 py-2.5 placeholder-gray-500 text-gray-900 focus:outline-none focus:ring-cyan-500 focus:border-cyan-500 focus:z-10 sm:text-sm p-2.5 ltr:pr-10 rtl:pl-10 ";

export default function InputWithImage({
  handleChange,
  blurChange,
  value,
  labelText,
  labelFor,
  id,
  name,
  type,
  isRequired = false,
  placeholder,
  customClass,
  imgPath,
  tooltipMsg = "vid_info",
  disabled = false,
  formError = "",
  passwordShowIcon = "images/password_show.svg",
  passwordHideIcon = "images/password_hide.svg",
  infoIcon = "images/info_icon.svg",
  i18nKeyPrefix1 = "tooltips",
  i18nKeyPrefix2 = "errors",
  icon,
  prefix,
  errorCode,
  maxLength = "",
  regex = "",
}) {

  const { t: t1 } = useTranslation("translation", { keyPrefix: i18nKeyPrefix1 });
  const { t: t2 } = useTranslation("translation", { keyPrefix: i18nKeyPrefix2 });

  const [showPassword, setShowPassword] = useState(false);
  const [errorBanner, setErrorBanner] = useState([]);

  const inputVal = useRef(value);

  const changePasswordState = () => {
    let passwordRef = document.getElementById(id);
    passwordRef.setAttribute("type", !showPassword ? "text" : "password");
    setShowPassword(!showPassword);
  };

  const handleKeyDown = (e) => {

    var keyCode = e.key || e.which;

    // multiKeyChecking function checks if the key is
    // ctrl + a, ctrl + c, ctrl + v
    // while pasting it will also check maxlength
    const multiKeyChecking = (key, ctrl, maxLength) => {
      if (
        ctrl &&
        (key === "a" || key === "c"
          || (key === "v" && checkMaxLength(maxLength)))
      ) {
        return true;
      }
      return false;
    };

    // checking max length for the input
    const checkMaxLength = (maxLength) =>
      maxLength === "" ? true : e.target.value.length < parseInt(maxLength);

    // testing with all input type
    // with respective regex
    const patternTest = (type, key) => {
      if (type === "number") {
        // Check if the pressed key is a number
        return /^\d$/.test(key);
      }
      if (type === "letter") {
        // Check if the pressed key is a letter (a-zA-Z)
        // Lower & upper case letters a-z
        // Prevent input of other characters
        return /^[a-zA-Z]$/.test(key);
      }
      if (type === "alpha-numeric") {
        // Check if the pressed key is a number (0-9) or a letter (a-zA-Z)
        // Numpad numbers 0-9
        // Prevent input of other characters
        return /^[a-zA-Z\d]$/.test(key);
      }

      return true
    }


    // Allow some special keys like Backspace, Tab, Home, End, Left Arrow, Right Arrow, Delete.
    const allowedKeyCodes =
      ['Backspace', 'Tab', 'Control', 'End', 'Home', 'ArrowLeft', 'ArrowRight', 'Delete'];

    if (!allowedKeyCodes.includes(keyCode) && !multiKeyChecking(keyCode, e.ctrlKey, maxLength)) {
      // checking max length for the input
      // if greater than the max length then prevent the default action
      if (!checkMaxLength(maxLength)) {
        e.preventDefault();
      }

      // checking patter for number, letter & alpha-numeric
      if (!patternTest(type, keyCode)) {
        e.preventDefault();
      }
    }
  }

  const onBlurChange = (e) => {
    const val = e.target.value;
    const id = e.target.id;
    const currentRegex = new RegExp(regex);
    let bannerIndex = errorBanner.findIndex((_) => _.id === id);
    let tempBanner = errorBanner.map((_) => {
      return { ..._, show: true };
    });
    // checking regex matching for username & password
    if (currentRegex.test(val) || val === "") {
      // if username or password is matched
      // then remove error from errorBanner
      if (bannerIndex > -1) {
        tempBanner.splice(bannerIndex, 1);
      }
    } else {
      // if username or password is not matched
      // with regex, then add the error
      if (bannerIndex === -1 && val !== "") {
        tempBanner.push({
          id,
          errorCode,
          show: true,
        });
      }
    }
    // setting the error in errorBanner
    setErrorBanner(tempBanner);
    blurChange(e, tempBanner)
  }

  return (
    <>
      <div className="flex items-center justify-between">
        <div className="flex justify-start">
          <label
            htmlFor={labelFor}
            className="block mb-2 text-xs font-medium text-gray-900 text-opacity-70"
          >
            {labelText}
          </label>
          {icon && (
            <PopoverContainer child={<img src={infoIcon} className="mx-1 mt-[2px] w-[15px] h-[14px] relative bottom-[1px]" />} content={id.includes("Otp") ? t1("otp_info") : id.includes("sbi") ? t1("bio_info") : id.includes("Pin") ? t1("pin_info") : t1("username_info")} position="right" contentSize="text-xs" contentClassName="rounded-md px-3 py-2 border border-[#BCBCBC] outline-0 bg-white shadow-md z-50 w-[175px] sm:w-[200px] md:w-[220px] leading-none"/>
          )}
        </div>
        {formError && (
          <label
            htmlFor={labelFor}
            className="font-medium text-xs text-red-600"
          >
            {formError}
          </label>
        )}
      </div>
      <div className={`relative input-box ${errorBanner && errorBanner.length > 0 && errorBanner.find(val => val.id === id) && "errorInput"}`}>
        {imgPath &&
          <div className="flex absolute inset-y-0 items-center p-3 pointer-events-none ltr:right-0 rtl:left-0 z-[11]">
            <img className="w-6 h-6" src={imgPath} />
          </div>
        }
        {prefix && prefix !== "" && <span className="prefix">{prefix}</span>}
        <input
          ref={inputVal}
          disabled={disabled}
          onChange={handleChange}
          onBlur={onBlurChange}
          onKeyDown={handleKeyDown}
          value={value}
          type={type}
          id={id}
          name={name}
          required={isRequired}
          className={fixedInputClass + customClass}
          placeholder={placeholder}
          title={t1(tooltipMsg)}
        />
        {id.includes("password") && (
          <span
            id="password-eye"
            type="button"
            className="flex absolute inset-y-0 p-3 pt-2 ltr:right-0 rtl:left-0 hover:cursor-pointer z-50"
            onClick={changePasswordState}
          >
            {showPassword ? (
              <img className="w-6 h-6" src={passwordShowIcon} />
            ) : (
              <img className="w-6 h-6" src={passwordHideIcon} />
            )}
          </span>
        )}
      </div>
      {
        errorBanner && errorBanner.length > 0 && errorBanner.map(item => {
          if (item.id === id) {
            return (
              <div className="bg-[#FAEFEF] text-[#D52929] text-sm pb-1 pt-[2px] px-2 rounded-b-md font-semibold" key={id}>
                {t2(`${item.errorCode}`)}
              </div>
            )
          }
          else return null;
        })
      }
    </>
  );
}
