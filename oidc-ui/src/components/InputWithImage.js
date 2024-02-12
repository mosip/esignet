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
  error
}) {

  const { t: t1 } = useTranslation("translation", { keyPrefix: i18nKeyPrefix1 });
  const { t: t2 } = useTranslation("translation", { keyPrefix: i18nKeyPrefix2 });

  const [showPassword, setShowPassword] = useState(false);
  const inputVal = useRef(value);

  const changePasswordState = () => {
    let passwordRef = document.getElementById(id);
    passwordRef.setAttribute("type", !showPassword ? "text" : "password");
    setShowPassword(!showPassword);
  };
  
  const handleKeyDown = (e) => {

    var keyCode = e.keyCode || e.which;
    
    // Allow some special keys like Backspace, Tab, Home, End, Left Arrow, Right Arrow, Delete.
    const allowedKeyCodes = [8, 9, 35, 36, 37, 39, 46];

    if (!allowedKeyCodes.includes(keyCode)) {

      if (type === "number") {

        // Check if the pressed key is a number
        if (keyCode < 48 || keyCode > 57) {
          // Prevent the default action (typing the letter or specified character)
          e.preventDefault();
        }
      }
  
      else if (type === "letter") {

        // Check if the pressed key is a letter (a-zA-Z)
        if (!((keyCode >= 65 && keyCode <= 90) ||     // Uppercase letters A-Z
        (keyCode >= 97 && keyCode <= 122))) {         // Lowercase letters a-z

          // Prevent input of other characters
          e.preventDefault();
        }
      }
  
      else if (type === "alpha-numeric") {

        // Check if the pressed key is a number (0-9) or a letter (a-zA-Z)
        if (!((keyCode >= 48 && keyCode <= 57) ||     // Numbers 0-9
        (keyCode >= 65 && keyCode <= 90) ||           // Uppercase letters A-Z
        (keyCode >= 97 && keyCode <= 122))) {         // Lowercase letters a-z  
                 
          // Prevent input of other characters
          e.preventDefault();
        }
      }
    }
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
          <PopoverContainer child={<img src={infoIcon} className="mx-1 mt-[2px] w-[15px] h-[14px]"/>} content={t1("username_info")} position="right" contentSize="text-xs"/>
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
      <div className={`relative input-box ${error && error.length > 0 && error.find(val => val.id === id) && "errorInput"}`}>
        {imgPath &&
          <div className="flex absolute inset-y-0 items-center p-3 pointer-events-none ltr:right-0 rtl:left-0">
            <img className="w-6 h-6" src={imgPath} />
          </div>
        }
        {prefix && prefix !== "" && <span className="prefix">{prefix}</span>}
        <input
          ref={inputVal}
          disabled={disabled}
          onChange={handleChange}
          onBlur={blurChange}
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
        error && error.length > 0 && error.map(item => {
          if(item.id === id) {
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
