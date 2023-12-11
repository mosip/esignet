import { useTranslation } from "react-i18next";
import { useLocation, useSearchParams } from "react-router-dom";
import { Buffer } from "buffer";

const fixedInputClass =
  "p-2 mt-1 mb-1 w-full text-center text-sm rounded-lg text-red-700 bg-red-100 ";

/**
 * @param {string} prefix optional error key which will be shown before the error msg.
 * @param {string} errorCode is a key from locales file under errors namespace
 * @param {string} defaultMsg (Optional) is a fallback value if transaction for errorCode not found.
 * If defaultMsg is not passed then errorCode key itself became the fallback value.
 */
const ErrorIndicator = ({
  prefix = "",
  errorCode,
  defaultMsg,
  i18nKeyPrefix = "errors",
  customClass,
}) => {
  const [searchParams, setSearchParams] = useSearchParams();
  const location = useLocation();

  const { t } = useTranslation("translation", { keyPrefix: i18nKeyPrefix });

  //Redirecting if transaction invalid
  if (errorCode === "invalid_transaction" || errorCode === "link_code_limit_reached") {
    let response = location.hash;

    if (!response) {
      //TODO naviagte to default error page
      return;
    }

    let nonce = searchParams.get("nonce");
    let state = searchParams.get("state");
    var decodeOAuth = Buffer.from(response, "base64")?.toString();
    var OAuthDetails = JSON.parse(decodeOAuth);

    let redirect_uri = OAuthDetails.redirectUri;

    if (!redirect_uri) {
      //TODO naviagte to default error page
      return;
    }

    window.onbeforeunload = null;
    
    let params = "?";
    if (nonce) {
      params = params + "nonce=" + nonce + "&";
    }
    params = params + "error_description=" + t("invalid_transaction", defaultMsg) + "&";

    //REQUIRED
    params = params + "state=" + state + "&";
    //REQUIRED
    params = params + "error=" + "invalid_transaction";

    window.location.replace(redirect_uri + params);
    return;
  }

  return (
    <div className={fixedInputClass + customClass} role="alert">
      {prefix && t(prefix) + ": "}
      {t(errorCode, defaultMsg)}
    </div>
  );
};

export default ErrorIndicator;
