import { buttonTypes } from "../constants/clientConstants";

export default function FormAction({
  handleClick,
  type = "Button", //valid values: Button, Submit and Reset
  text,
  disabled = false,
  id,
}) {
  const className =
    "flex justify-center w-full font-medium rounded-lg text-sm px-5 py-2 text-center border border-2 ";

  return (
    <>
      {type === buttonTypes.button && (
        <button
          type={type}
          value={type}
          className={className + " primary-button"}
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
          className={className + " primary-button"}
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
          className={className + " primary-button"}
          onClick={handleClick}
          disabled={disabled}
        >
          {text}
        </button>
      )}
      {type === buttonTypes.cancel && (
        <button
          type={type}
          value={type}
          className={
            className + " secondary-button"
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
            className + "secondary-button discontinue-button"
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
