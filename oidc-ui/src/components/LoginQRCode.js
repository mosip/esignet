import QRCode from "qrcode";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import ErrorIndicator from "../common/ErrorIndicator";
import LoadingIndicator from "../common/LoadingIndicator";
import {
  configurationKeys,
  deepLinkParamPlaceholder,
} from "../constants/clientConstants";
import { LoadingStates as states } from "../constants/states";

var linkAuthTriggered = false;

export default function LoginQRCode({
  linkAuthService,
  openIDConnectService,
  i18nKeyPrefix = "LoginQRCode",
}) {
  const post_GenerateLinkCode = linkAuthService.post_GenerateLinkCode;
  const post_LinkStatus = linkAuthService.post_LinkStatus;
  const post_AuthorizationCode = linkAuthService.post_AuthorizationCode;

  const { t } = useTranslation("translation", { keyPrefix: i18nKeyPrefix });
  const [qr, setQr] = useState("");
  const [status, setStatus] = useState({ state: states.LOADED, msg: "" });
  const [error, setError] = useState(null);

  const linkAuthCodeExpireInSec =
    openIDConnectService.getEsignetConfiguration(
      configurationKeys.linkAuthCodeExpireInSec
    ) ?? process.env.REACT_APP_LINK_AUTH_CODE_TIMEOUT_IN_SEC;

  /*
  linkCodeDeferredTimeoutInSec is link_status Grace period. link-status request will not be triggered 
  if the linkCode is going to expire within grace period.
  */
  const linkCodeDeferredTimeoutInSec =
    openIDConnectService.getEsignetConfiguration(
      configurationKeys.linkCodeDeferredTimeoutInSec
    ) ?? process.env.REACT_APP_LINK_CODE_DEFERRED_TIMEOUT_IN_SEC;

  let parseTimeout = parseInt(linkCodeDeferredTimeoutInSec);

  const linkStatusGracePeriod = parseTimeout !== "NaN" ? parseTimeout : 25;

  const walletLogoURL =
  openIDConnectService.getEsignetConfiguration(
    configurationKeys.walletLogoURL
  ) ?? process.env.REACT_APP_WALLET_LOGO_URL;

  const GenerateQRCode = (response, logoUrl) => {
    let text =
      openIDConnectService.getEsignetConfiguration(
        configurationKeys.qrCodeDeepLinkURI
      ) ?? process.env.REACT_APP_QRCODE_DEEP_LINK_URI;

    text = text.replace(deepLinkParamPlaceholder.linkCode, response.linkCode);

    text = text.replace(
      deepLinkParamPlaceholder.linkExpiryDate,
      response.expireDateTime
    );

    const canvas = document.createElement("canvas");
    QRCode.toCanvas(
      canvas,
      text,
      {
        width: 500,
        margin: 2,
        color: {
          dark: "#000000",
        },
      },
      (err) => {
        if (err) {
          setError({
            errorCode: "link_code_refresh_failed",
          });
          return;
        }
        if (logoUrl) {
          const logo = new Image();
          logo.src = logoUrl;
          logo.onload = () => {
            const ctx = canvas.getContext("2d");
            const size = canvas.width / 6;
            const x = (canvas.width - size) / 2;
            const y = (canvas.height - size) / 2;
            // Create a new canvas to filter the logo image
            const filterCanvas = document.createElement("canvas");
            filterCanvas.width = logo.width;
            filterCanvas.height = logo.height;
            const filterCtx = filterCanvas.getContext("2d");
            filterCtx.drawImage(logo, 0, 0);
            ctx.fillStyle = "#000000";
            ctx.fillRect(x - 6, y - 6, size + 12, size + 12);
            // Draw the filtered image onto the QR code canvas
            ctx.fillStyle = "#ffffff";
            ctx.fillRect(200, 200, 100, 100);
            ctx.drawImage(filterCanvas, x, y, size, size);
            setQr(canvas.toDataURL());
          };
          logo.onerror = () => {
            // If there's an error fetching the logo, generate QR code without the logo
            setQr(canvas.toDataURL());
          }
        }
        else {
          // If logoUrl is not configured, generate QR code without the logo
          setQr(canvas.toDataURL());
        }
      }
    );
  };

  useEffect(() => {
    fetchQRCode();
  }, []);

  const fetchQRCode = async () => {
    setQr("");
    setError(null);
    try {
      setStatus({
        state: states.LOADING,
        msg: "loading_msg",
      });
      let { response, errors } = await post_GenerateLinkCode(
        openIDConnectService.getTransactionId()
      );

      if (errors != null && errors.length > 0) {
        setError({
          errorCode: errors[0].errorCode,
          defaultMsg: errors[0].errorMessage,
        });
      } else {
        GenerateQRCode(response, walletLogoURL);
        setStatus({ state: states.LOADED, msg: "" });
        triggerLinkStatus(
          response.transactionId,
          response.linkCode,
          response.expireDateTime
        );
      }
    } catch (error) {
      setError({
        prefix: "link_code_refresh_failed",
        errorCode: error.message,
        defaultMsg: error.message,
      });
    }
  };

  const triggerLinkStatus = async (
    transactionId,
    linkCode,
    linkCodeExpiryDateTime
  ) => {
    try {
      let expiryDateTime = new Date(linkCodeExpiryDateTime);
      let timeLeft = (expiryDateTime - new Date()) / 1000; // timeleft in sec
      let qrExpired = false;
      let linkStatusResponse;
      while (timeLeft > 0) {
        try {
          linkStatusResponse = await post_LinkStatus(transactionId, linkCode);
        } catch {
          //ignore
        }

        if (linkAuthTriggered) break;

        //return if invalid transactionId;
        if (linkStatusResponse?.errors[0] === "invalid_transaction") {
          setError({
            errorCode: linkStatusResponse.errors[0].errorCode,
            defaultMsg: linkStatusResponse.errors[0].errorMessage,
          });
          return;
        }

        //Break if response is returned
        if (linkStatusResponse?.response) {
          break;
        }

        timeLeft = (expiryDateTime - new Date()) / 1000;
        if (
          !qrExpired &&
          timeLeft < linkStatusGracePeriod &&
          (!linkStatusResponse || !linkStatusResponse?.response)
        ) {
          qrExpired = true;
          // setError({
          //   errorCode: "qr_code_expired",
          // });
          fetchQRCode();
        }
      }

      if (linkAuthTriggered) return;

      if (
        linkStatusResponse?.errors != null &&
        linkStatusResponse?.length > 0
      ) {
        setError({
          errorCode: linkStatusResponse.errors[0].errorCode,
          defaultMsg: linkStatusResponse.errors[0].errorMessage,
        });
      } else if (linkStatusResponse?.response) {
        let response = linkStatusResponse.response;
        if (response.linkStatus != "LINKED") {
          setError({
            errorCode: "failed_to_link",
          });
        } else {
          setError(null);
          setQr(null);
          setStatus({
            state: states.LOADING,
            msg: "link_auth_waiting",
          });
          triggerLinkAuth(transactionId, linkCode);
        }
      }
    } catch (error) {
      setError({
        prefix: "link_code_refresh_failed",
        errorCode: error.message,
        defaultMsg: error.message,
      });
    }
  };

  const triggerLinkAuth = async (transactionId, linkedCode) => {
    linkAuthTriggered = true;
    try {
      let codeExpiryDateTime = new Date();
      codeExpiryDateTime.setSeconds(
        codeExpiryDateTime.getSeconds() + Number(linkAuthCodeExpireInSec)
      );
      let timeLeft = (codeExpiryDateTime - new Date()) / 1000;
      let linkAuthResponse;
      while (timeLeft > 0) {
        try {
          linkAuthResponse = await post_AuthorizationCode(
            transactionId,
            linkedCode
          );
        } catch {
          //ignore
        }

        //return if invalid transactionId;
        if (linkAuthResponse?.errors[0] === "invalid_transaction") {
          setError({
            errorCode: linkAuthResponse.errors[0].errorCode,
            defaultMsg: linkAuthResponse.errors[0].errorMessage,
          });
          return;
        }

        //Break if response is returned
        if (linkAuthResponse?.response) {
          break;
        }

        timeLeft = (codeExpiryDateTime - new Date()) / 1000;
      }

      //No response
      if (!linkAuthResponse || !linkAuthResponse?.response) {
        setError({
          errorCode: "authorization_failed",
        });
        return;
      }

      if (
        linkAuthResponse?.errors != null &&
        linkAuthResponse?.errors.length > 0
      ) {
        setError({
          errorCode: linkAuthResponse.errors[0].errorCode,
          defaultMsg: linkAuthResponse.errors[0].errorMessage,
        });
      } else {
        setStatus({
          state: states.LOADING,
          msg: "redirecting_msg",
        });

        let response = linkAuthResponse.response;
        //Redirect
        let params = "?";
        if (response.nonce) {
          params = params + "nonce=" + response.nonce + "&";
        }

        if (response.state) {
          params = params + "state=" + response.state + "&";
        }

        window.location.replace(
          response.redirectUri + params + "code=" + response.code
        );
      }
    } catch (error) {
      setError({
        prefix: "link_code_status_failed",
        errorCode: error.message,
        defaultMsg: error.message,
      });
    }
  };

  return (
    <>
      <h1
        className="text-center text-sky-600 font-semibold line-clamp-2"
        title={t("scan_with_inji")}
      >
        {t("scan_with_inji")}
      </h1>
      <div className="relative h-64 mt-6">
        {error && (
          <div className="absolute bottom-0 left-0 bg-white bg-opacity-90 h-full w-full flex justify-center items-center">
            <div className="rounded h-min w-full p-3 mx-4">
              <ErrorIndicator
                prefix={error.prefix}
                errorCode={error.errorCode}
                defaultMsg={error.defaultMsg}
                customClass="font-semibold"
              />
              <div className="flex w-full justify-center mt-5">
                <button
                  type="button"
                  id="refresh_qr_code"
                  className="flex justify-center w-full text-gray-900 bg-slate-200 border border-gray-300 hover:bg-gray-100 font-medium rounded-lg text-sm px-5 py-2.5"
                  onClick={fetchQRCode}
                >
                  {t("refresh")}
                </button>
              </div>
            </div>
          </div>
        )}
        {qr && (
          <div className="w-full flex justify-center">
            <div className="border border-4 border-sky-600 rounded-3xl p-2 w-64 h-64">
              <img src={qr} />
            </div>
          </div>
        )}
        {status.state === states.LOADING && error === null && (
          <div className="absolute bottom-0 left-0 bg-white bg-opacity-80 h-full w-full flex justify-center items-center">
            <div className="rounded h-min bg-slate-50 w-full p-3 mx-4">
              <LoadingIndicator size="medium" message={status.msg} />
            </div>
          </div>
        )}
      </div>
    </>
  );
}
