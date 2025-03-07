import { useState, useRef, useLayoutEffect } from "react";
import { useTranslation } from "react-i18next";
import PopoverContainer from "../common/Popover";

const fixedInputClass =
  "rounded-md bg-white shadow-lg appearance-none block w-full px-3.5 py-2.5 placeholder-[#A0A8AC] text-gray-900 focus:outline-none focus:ring-cyan-500 focus:border-cyan-500 focus:z-10 sm:text-sm p-2.5 ltr:pr-10 rtl:pl-10 ";

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
  isInvalid,
  individualId,
  currenti18nPrefix,
}) {
  const { t, i18n } = useTranslation("translation");
  const { t: t1 } = useTranslation("translation", {
    keyPrefix: i18nKeyPrefix1,
  });
  const { t: t2 } = useTranslation("translation", {
    keyPrefix: i18nKeyPrefix2,
  });

  const [showPassword, setShowPassword] = useState(false);
  const [errorBanner, setErrorBanner] = useState([]);
  const [isCapsLockOn, setIsCapsLockOn] = useState(false);

  const inputVal = useRef(value);

  useLayoutEffect(() => {
    if (inputVal.current) {
      inputVal.current.focus();
    }
  }, [i18n.language]);

  const changePasswordState = () => {
    let passwordRef = document.getElementById(id);
    passwordRef.setAttribute("type", !showPassword ? "text" : "password");
    setShowPassword(!showPassword);
  };

  const handleKeyUp = (e) => {
    setIsCapsLockOn(e.getModifierState("CapsLock"));
  };

  const handleKeyDown = (e) => {
    var keyCode = e.key || e.which;

    // multiKeyChecking function checks if the key is
    // ctrl + a, ctrl + c, ctrl + v
    // while pasting it will also check maxlength
    const multiKeyChecking = (key, ctrl, maxLength) => {
      if (
        ctrl &&
        (key === "a" ||
          key === "c" ||
          (key === "v" && checkMaxLength(maxLength)))
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

      return true;
    };

    // Allow some special keys like Backspace, Tab, Home, End, Left Arrow, Right Arrow, Delete.
    const allowedKeyCodes = [
      "Backspace",
      "Tab",
      "Control",
      "End",
      "Home",
      "ArrowLeft",
      "ArrowRight",
      "Delete",
    ];

    if (
      !allowedKeyCodes.includes(keyCode) &&
      !multiKeyChecking(keyCode, e.ctrlKey, maxLength)
    ) {
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

    setIsCapsLockOn(e.getModifierState("CapsLock"));
  };

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
    blurChange(e, tempBanner);
  };

  return (
    <>
      <div className="flex items-center justify-between">
        <div className="flex justify-start w-full">
          <div className="flex justify-between w-full">
            <label
              className="inline-block mt-4 mb-2 text-sm font-medium"
              htmlFor={id}
            >
              {labelText}
            </label>
            {id.includes("Password") &&
              type === "password" &&
              value &&
              isCapsLockOn && (
                <small className="caps_lock flex self-center mt-2 w-auto">
                  <svg
                    xmlns="http://www.w3.org/2000/svg"
                    width="16"
                    height="16"
                    viewBox="0 0 18.5 18.5"
                    className="mr-1 self-center w-3 h-3"
                  >
                    <g
                      id="info_FILL0_wght400_GRAD0_opsz48"
                      transform="translate(0.25 0.25)"
                    >
                      <path
                        id="info_FILL0_wght400_GRAD0_opsz48-2"
                        data-name="info_FILL0_wght400_GRAD0_opsz48"
                        d="M88.393-866.5h1.35v-5.4h-1.35ZM89-873.565a.731.731,0,0,0,.529-.207.685.685,0,0,0,.214-.513.752.752,0,0,0-.213-.545.707.707,0,0,0-.529-.22.708.708,0,0,0-.529.22.751.751,0,0,0-.214.545.686.686,0,0,0,.213.513A.729.729,0,0,0,89-873.565ZM89.006-862a8.712,8.712,0,0,1-3.5-.709,9.145,9.145,0,0,1-2.863-1.935,9.14,9.14,0,0,1-1.935-2.865,8.728,8.728,0,0,1-.709-3.5,8.728,8.728,0,0,1,.709-3.5,9,9,0,0,1,1.935-2.854,9.237,9.237,0,0,1,2.865-1.924,8.728,8.728,0,0,1,3.5-.709,8.728,8.728,0,0,1,3.5.709,9.1,9.1,0,0,1,2.854,1.924,9.089,9.089,0,0,1,1.924,2.858,8.749,8.749,0,0,1,.709,3.5,8.712,8.712,0,0,1-.709,3.5,9.192,9.192,0,0,1-1.924,2.859,9.087,9.087,0,0,1-2.857,1.935A8.707,8.707,0,0,1,89.006-862Zm.005-1.35a7.348,7.348,0,0,0,5.411-2.239,7.4,7.4,0,0,0,2.228-5.422,7.374,7.374,0,0,0-2.223-5.411A7.376,7.376,0,0,0,89-878.65a7.4,7.4,0,0,0-5.411,2.223A7.357,7.357,0,0,0,81.35-871a7.372,7.372,0,0,0,2.239,5.411A7.385,7.385,0,0,0,89.011-863.35ZM89-871Z"
                        transform="translate(-80 880)"
                        fill="#2D86BA"
                        stroke="#2D86BA"
                        stroke-width="0.5"
                      />
                    </g>
                  </svg>
                  <span className="items-end">{t1("caps_on")}</span>
                </small>
              )}
          </div>
          {icon && (
            <PopoverContainer
              child={
                <img
                  src={infoIcon}
                  className="mx-1 mt-[2px] w-[15px] h-[14px] relative bottom-[1px]"
                />
              }
              content={
                id.includes("Otp")
                  ? t1("otp_info")
                  : id.includes("sbi")
                  ? t1("bio_info")
                  : id.includes("Pin")
                  ? t1("pin_info")
                  : t1("username_info")
              }
              position="right"
              contentSize="text-xs"
              contentClassName="rounded-md px-3 py-2 border border-[#BCBCBC] outline-0 bg-white shadow-md z-50 w-[175px] sm:w-[200px] md:w-[220px] leading-none"
            />
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
      <div
        className={`relative input-box ${
          ((isInvalid && individualId?.length > 0 && type !== "password") ||
            (errorBanner &&
              errorBanner.length > 0 &&
              errorBanner.find((val) => val.id === id))) &&
          "!border-[#FE6B6B]"
        }`}
      >
        {imgPath && (
          <div className="flex absolute inset-y-0 items-center p-3 pointer-events-none ltr:right-0 rtl:left-0 z-[11]">
            <img className="w-6 h-6" src={imgPath} />
          </div>
        )}
        {prefix && prefix !== "" && <span className="prefix">{prefix}</span>}
        <input
          ref={inputVal}
          disabled={disabled}
          onChange={handleChange}
          onBlur={onBlurChange}
          onKeyDown={handleKeyDown}
          onKeyUp={handleKeyUp}
          // value={value}
          type={type}
          id={id}
          name={name}
          required={isRequired}
          className={fixedInputClass + customClass}
          placeholder={placeholder}
          title={t1(tooltipMsg)}
          onWheelCapture={(e) => e.target.blur()}
        />
        {id.includes("Password") && type === "password" && value && (
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

      {((isInvalid && individualId?.length > 0) ||
        (errorBanner && errorBanner.length > 0)) && (
        <small className="text-[#FE6B6B] font-medium flex items-center !mt-1">
          {type !== "password" ? (
            <>
              <span>
                <img
                  src="\images\error_icon.svg"
                  alt="error_icon"
                  className="mr-1"
                  width="12px"
                />
              </span>
              <span>{`${t(`${currenti18nPrefix}.invalid_input`, {
                id: `${t(
                  `${currenti18nPrefix}.${
                    id.includes("_") ? id.split("_")[1] : id
                  }`
                )}`,
              })}`}</span>
            </>
          ) : (
            errorBanner &&
            errorBanner.length > 0 &&
            errorBanner.map((item) => {
              if (item.id === id) {
                return (
                  <>
                    <span>
                      <img
                        src="\images\error_icon.svg"
                        alt="error_icon"
                        className="mr-1"
                        width="12px"
                      />
                    </span>
                    <span>{t2(`${item.errorCode}`)}</span>
                  </>
                );
              }
            })
          )}
        </small>
      )}
    </>
  );
}
