import { useTranslation } from "react-i18next";

const fixedInputClass =
  "rounded-md appearance-none relative block w-full px-3 py-2 border border-gray-300 placeholder-gray-500 text-gray-900 focus:outline-none focus:ring-cyan-500 focus:border-cyan-500 focus:z-10 sm:text-sm";

export default function Input({
  handleChange,
  value,
  labelText,
  labelFor,
  id,
  name,
  type,
  tooltipMsg = "vid_tooltip",
  isRequired = false,
  placeholder,
  customClass,
  i18nKeyPrefix = "tooltips",
}) {
  const { t } = useTranslation("translation", { keyPrefix: i18nKeyPrefix });
  return (
    <div className="my-5">
      <label htmlFor={labelFor} className="sr-only">
        {labelText}
      </label>
      <input
        onChange={handleChange}
        value={value}
        id={id}
        name={name}
        type={type}
        required={isRequired}
        className={fixedInputClass + customClass}
        placeholder={placeholder}
        title={t(tooltipMsg)}
      />
    </div>
  );
}
