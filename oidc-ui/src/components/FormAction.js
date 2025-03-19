import { buttonTypes } from "../constants/clientConstants";

export default function FormAction({
  handleClick,
  type = "Button", //valid values: Button, Submit and Reset
  text,
  disabled = false,
  id,
  customClassName
}) {
  const className =
    "flex justify-center w-full font-semibold rounded-md px-5 py-3 text-center border-2 ";

  return (
    <>
      {type === buttonTypes.button && (
        <button
          type={type}
          value={type}
          className={className + " primary-button " + customClassName}
          onClick={handleClick}
          disabled={disabled}
          id={id}
        >
          {text}
        </button>
      )}
      {type === buttonTypes.submit && (
        <button
          type={type}
          value={type}
          className={className + " primary-button " + customClassName}
          onSubmit={handleClick}
          disabled={disabled}
          id={id}
        >
          {text}
        </button>
      )}
      {type === buttonTypes.reset && (
        <button
          type={type}
          value={type}
          className={className + " primary-button " + customClassName}
          onClick={handleClick}
          disabled={disabled}
          id={id}
        >
          {text}
        </button>
      )}
      {type === buttonTypes.cancel && (
        <button
          type={type}
          value={type}
          className={
            className + " secondary-button " + customClassName
          }
          onClick={handleClick}
          disabled={disabled}
          id={id}
        >
          {text}
        </button>
      )}
      {type === buttonTypes.discontinue && (
        <button
          type={type}
          value={type}
          className={
            className + "secondary-button discontinue-button " + customClassName
          }
          onClick={handleClick}
          disabled={disabled}
          id={id}
        >
          {text}
        </button>
      )}
    </>
  );
}
