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
  i18nKeyPrefix = "tooltips",
  icon,
  prefix
}) {
  const { t } = useTranslation("translation", { keyPrefix: i18nKeyPrefix });

  const [showPassword, setShowPassword] = useState(false);
  const inputVal = useRef(value);

  const changePasswordState = () => {
    let passwordRef = document.getElementById(id);
    passwordRef.setAttribute("type", !showPassword ? "text" : "password");
    setShowPassword(!showPassword);
  };

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
          <PopoverContainer child={<img src={infoIcon} className="mx-1 mt-[2px] w-[15px] h-[14px]"/>} content={t("username_info")} position="right" contentSize="text-xs"/>
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
      <div className="relative input-box">
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
          value={value}
          type={type}
          id={id}
          name={name}
          required={isRequired}
          className={fixedInputClass + customClass}
          placeholder={placeholder}
          title={t(tooltipMsg)}
        />
        {type === "password" && inputVal.current.value !== "" && (
          <span
            className="flex absolute inset-y-0 p-3 pt-2 ltr:right-0 rtl:left-0 cursor-pointer"
            onClick={changePasswordState}
          >
            {showPassword ? (
              <img className="w-6 h-6" src={passwordHideIcon} />
            ) : (
              <img className="w-6 h-6" src={passwordShowIcon} />
            )}
          </span>
        )}
      </div>
    </>
  );
}
