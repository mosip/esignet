import { buttonTypes } from "../constants/clientConstants";

export default function FormAction({
  handleClick,
  type = "Button", //valid values: Button, Submit and Reset
  text,
  disabled = false,
}) {
  const className =
    "flex justify-center w-full font-medium rounded-lg text-sm px-5 py-2 text-center border border-2 ";

  return (
    <>
      {type === buttonTypes.button && (
        <button
          type={type}
          value={type}
          className={
            className +
            (disabled
              ? "text-slate-400 bg-white"
              : "text-white bg-gradient-to-t from-cyan-500 to-blue-500 hover:bg-gradient-to-b")
          }
          onClick={handleClick}
          disabled={disabled}
        >
          {text}
        </button>
      )}
      {type === buttonTypes.submit && (
        <button
          type={type}
          value={type}
          className={
            className +
            (disabled
              ? "text-slate-400 bg-white"
              : "text-white bg-gradient-to-t from-cyan-500 to-blue-500 hover:bg-gradient-to-b")
          }
          onSubmit={handleClick}
          disabled={disabled}
        >
          {text}
        </button>
      )}
      {type === buttonTypes.reset && (
        <button
          type={type}
          value={type}
          className={
            className +
            (disabled
              ? "text-slate-400 bg-white"
              : "text-white bg-gradient-to-t from-cyan-500 to-blue-500 hover:bg-gradient-to-b")
          }
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
            className +
            (disabled
              ? "text-slate-400 bg-white"
              : "text-gray-900 bg-white hover:bg-gray-100")
          }
          onClick={handleClick}
          disabled={disabled}
        >
          {text}
        </button>
      )}
    </>
  );
}
