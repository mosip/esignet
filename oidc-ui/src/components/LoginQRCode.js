import QRCode from "qrcode";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import ErrorIndicator from "../common/ErrorIndicator";
import LoadingIndicator from "../common/LoadingIndicator";
import {
  configurationKeys,
  deepLinkParamPlaceholder,
  walletConfigKeys,
} from "../constants/clientConstants";
import { LoadingStates as states } from "../constants/states";
import ErrorBanner from "../common/ErrorBanner";
import langConfigService from "../services/langConfigService";
import redirectOnError from "../helpers/redirectOnError";

var linkAuthTriggered = false;
const langConfig = await langConfigService.getEnLocaleConfiguration();  

export default function LoginQRCode({
  walletDetail,
  linkAuthService,
  openIDConnectService,
  backButtonDiv,
  i18nKeyPrefix1 = "LoginQRCode",
  i18nKeyPrefix2 = "errors"
}) {
  const post_GenerateLinkCode = linkAuthService.post_GenerateLinkCode;
  const post_LinkStatus = linkAuthService.post_LinkStatus;
  const post_AuthorizationCode = linkAuthService.post_AuthorizationCode;

  const { t: t1 } = useTranslation("translation", { keyPrefix: i18nKeyPrefix1 });
  const { t: t2 } = useTranslation("translation", { keyPrefix: i18nKeyPrefix2 });

  const [qr, setQr] = useState("");
  const [status, setStatus] = useState({ state: states.LOADED, msg: "" });
  const [error, setError] = useState(null);
  const [qrCodeTimeOut, setQrCodeTimeout] = useState();
  const [errorBanner, setErrorBanner] = useState(null);

  const linkedTransactionExpireInSec =
    openIDConnectService.getEsignetConfiguration(
      configurationKeys.linkedTransactionExpireInSecs
    ) ?? process.env.REACT_APP_LINKED_TRANSACTION_EXPIRE_IN_SEC;

  /*
  The QRCode will be valid even after expiring on the UI for the period of qrCodeBufferInSecs.
  */
  const qrCodeBufferInSecs =
    openIDConnectService.getEsignetConfiguration(
      configurationKeys.qrCodeBufferInSecs
    ) ?? process.env.REACT_APP_QR_CODE_BUFFER_IN_SEC;

  const qrCodeBuffer =
    parseInt(qrCodeBufferInSecs) !== "NaN"
      ? parseInt(qrCodeBufferInSecs)
      : process.env.REACT_APP_QR_CODE_BUFFER_IN_SEC;

  const walletLogoURL =
    walletDetail[walletConfigKeys.walletLogoUrl] ??
    process.env.REACT_APP_WALLET_LOGO_URL;

  let qrCodeDeepLinkURI =
    walletDetail[walletConfigKeys.qrCodeDeepLinkURI] ??
    process.env.REACT_APP_QRCODE_DEEP_LINK_URI;

  const walletQrCodeAutoRefreshLimit =
    openIDConnectService.getEsignetConfiguration(
      configurationKeys.walletQrCodeAutoRefreshLimit
    ) ?? process.env.REACT_APP_WALLET_QR_CODE_AUTO_REFRESH_LIMIT;

  const GenerateQRCode = (response, logoUrl) => {
    let text = qrCodeDeepLinkURI.replace(
      deepLinkParamPlaceholder.linkCode,
      response.linkCode
    );

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
          setErrorBanner({
            errorCode: "wallet.link_code_refresh_failed",
            show: true
          });
          return;
        }
        if (logoUrl) {
          const logo = new Image();
          logo.src = logoUrl;
          logo.crossOrigin = "anonymous";
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
          };
        } else {
          // If logoUrl is not configured, generate QR code without the logo
          setQr(canvas.toDataURL());
        }
      }
    );
  };

  useEffect(() => {
    fetchQRCode();

    return () => {
      //clearing timeout before component unmount
      clearTimeout(qrCodeTimeOut);
    };
  }, []);

  let qrCodeRefreshCount = 0;

  const fetchQRCode = async () => {
    // If successfulFetchQrCount is 3, stop QR code generation and show QR code expired with a refresh button.
    if (qrCodeRefreshCount >= walletQrCodeAutoRefreshLimit) {
      setErrorBanner({
        errorCode: "wallet.qr_code_expired",
        show: true
      });
      return;
    }
    setQr("");
    setErrorBanner(null);
    try {
      setStatus({
        state: states.LOADING,
        msg: "loading_msg",
      });
      let { response, errors } = await post_GenerateLinkCode(
        openIDConnectService.getTransactionId()
      );

      if (errors != null && errors.length > 0) {

        let errorCodeCondition = langConfig.errors.wallet[errors[0].errorCode] !== undefined && langConfig.errors.wallet[errors[0].errorCode] !== null;

        if (errorCodeCondition) {
          setErrorBanner({
            errorCode: `wallet.${errors[0].errorCode}`,
            show: true
          });
        }
        else if (errors[0].errorCode === "invalid_transaction") {
          redirectOnError(errors[0].errorCode, t2(`${errors[0].errorCode}`));
        }
        else {
          setErrorBanner({
            errorCode: `${errors[0].errorCode}`,
            show: true
          });
        }
      } else {
        let qrCodeExpiryDateTime = new Date(response.expireDateTime);
        let timeLeft = (qrCodeExpiryDateTime - new Date()) / 1000; // timeleft in sec

        if (qrCodeBuffer > (timeLeft + 1) / 2) {
          /* 
            qrCodeBuffer should not be greater then the half of link-code-expire-in-secs.
            It reduces the chances of more then 2 active link-status polling request at a time.
           */
          setErrorBanner({
            errorCode: "wallet.invalid_qrcode_config",
            show: true
          });
          return;
        }

        /* 
          considering buffer time before next qrcode render, which will allow previous
          qrcode's link-status polling request to be active for the buffer period.
        */
        let timeLeftWithBuffer = timeLeft - qrCodeBuffer;

        GenerateQRCode(response, walletLogoURL);
        setStatus({ state: states.LOADED, msg: "" });
        triggerLinkStatus(
          response.transactionId,
          response.linkCode,
          response.expireDateTime
        );

        clearTimeout(qrCodeTimeOut);
        let _timer = setTimeout(() => {
          if (linkAuthTriggered) return;
          fetchQRCode();
        }, timeLeftWithBuffer * 1000);
        setQrCodeTimeout(_timer);
        qrCodeRefreshCount++;
      }
    } catch (error) {
      setErrorBanner({
        errorCode: "wallet.link_code_refresh_failed",
        show: true
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

        let errorCodeCondition = langConfig.errors.password[linkStatusResponse.errors[0].errorCode] !== undefined && langConfig.errors.password[linkStatusResponse.errors[0].errorCode] !== null;

        if (errorCodeCondition) {
          setErrorBanner({
            errorCode: `password.${linkStatusResponse.errors[0].errorCode}`,
            show: true
          });
        }
        else {
          setErrorBanner({
            errorCode: `${linkStatusResponse.errors[0].errorCode}`,
            show: true
          });
        }
          return;
        }

        //Break if response is returned
        if (linkStatusResponse?.response) {
          break;
        }

        timeLeft = (expiryDateTime - new Date()) / 1000;
      }

      if (linkAuthTriggered) return;

      if (
        linkStatusResponse?.errors != null &&
        linkStatusResponse?.length > 0
      ) {

        let errorCodeCondition = langConfig.errors.password[linkStatusResponse.errors[0].errorCode] !== undefined && langConfig.errors.password[linkStatusResponse.errors[0].errorCode] !== null;

        if (errorCodeCondition) {
          setErrorBanner({
            errorCode: `password.${linkStatusResponse.errors[0].errorCode}`,
            show: true
          });
        }
        else if (linkStatusResponse.errors[0].errorCode === "invalid_transaction") {
          redirectOnError(linkStatusResponse.errors[0].errorCode, t2(`${linkStatusResponse.errors[0].errorCode}`));
        }
        else {
          setErrorBanner({
            errorCode: `${linkStatusResponse.errors[0].errorCode}`,
            show: true
          });
        }
      } else if (linkStatusResponse?.response) {
        let response = linkStatusResponse.response;
        if (response.linkStatus != "LINKED") {
          setErrorBanner({
            errorCode: "wallet.failed_to_link",
            show: true
          });
        } else {
          setErrorBanner(null);
          setQr(null);
          setStatus({
            state: states.LOADING,
            msg: "link_auth_waiting",
            msgParam: { walletName: walletDetail[walletConfigKeys.walletName] },
          });
          triggerLinkAuth(transactionId, linkCode);
        }
      }
    } catch (error) {
      setErrorBanner({
        errorCode: "wallet.link_code_refresh_failed",
        show: true
      });
    }
  };

  const triggerLinkAuth = async (transactionId, linkedCode) => {
    linkAuthTriggered = true;
    try {
      let codeExpiryDateTime = new Date();
      codeExpiryDateTime.setSeconds(
        codeExpiryDateTime.getSeconds() + Number(linkedTransactionExpireInSec)
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

          let errorCodeCondition = langConfig.errors.password[linkAuthResponse.errors[0].errorCode] !== undefined && langConfig.errors.password[linkAuthResponse.errors[0].errorCode] !== null;

          if (errorCodeCondition) {
            setErrorBanner({
              errorCode: `password.${linkAuthResponse.errors[0].errorCode}`,
              show: true
            });
          }
          else {
            setErrorBanner({
              errorCode: `${linkAuthResponse.errors[0].errorCode}`,
              show: true
            });
          }
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
        setErrorBanner({
          errorCode: "authorization_failed",
          show: true
        });
        return;
      }

      if (
        linkAuthResponse?.errors != null &&
        linkAuthResponse?.errors.length > 0
      ) {
        let errorCodeCondition = langConfig.errors.password[linkAuthResponse.errors[0].errorCode] !== undefined && langConfig.errors.password[linkAuthResponse.errors[0].errorCode] !== null;

        if (errorCodeCondition) {
          setErrorBanner({
            errorCode: `password.${linkAuthResponse.errors[0].errorCode}`,
            show: true
          });
        }
        else if (linkAuthResponse.errors[0].errorCode === "invalid_transaction") {
          redirectOnError(linkAuthResponse.errors[0].errorCode, t2(`${linkAuthResponse.errors[0].errorCode}`));
        }
        else {
          setErrorBanner({
            errorCode: `${linkAuthResponse.errors[0].errorCode}`,
            show: true
          });
        }
      } else {
        setStatus({
          state: states.LOADING,
          msg: "redirecting_msg",
        });

        window.onbeforeunload = null;
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
      setErrorBanner({
        errorCode: "wallet.link_code_status_failed",
        show: true
      });
    }
  };

  const onCloseHandle = () => {
    setErrorBanner(null);
  };

  return (
    <>
      <div className="grid grid-cols-8 items-center">
        {backButtonDiv}
      </div>

      {errorBanner !== null && (
        <ErrorBanner
          showBanner={errorBanner.show}
          errorCode={t2(errorBanner.errorCode)}
          onCloseHandle={onCloseHandle}
        />
      )}

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
                  {t1("refresh")}
                </button>
              </div>
            </div>
          </div>
        )}
        {qr && (
          <div className="w-full flex justify-center">
            <div className="border border-4 qrcode-border rounded-3xl p-2">
              <img id="wallet-qr-code" src={qr} style={{ height: "186px", width: "186px" }} />
            </div>
          </div>
        )}
        {status.state === states.LOADING && error === null && (
          <div className="absolute bottom-0 left-0 bg-white bg-opacity-80 h-full w-full flex justify-center items-center">
            <div className="rounded h-min loading-indicator w-full p-3 mx-4">
              <LoadingIndicator
                size="medium"
                message={status.msg}
                msgParam={status.msgParam}
              />
            </div>
          </div>
        )}
      </div>

      {/**footer */}
      <div className="row-span-1 mt-5 mb-2">
        <div>
          <p className="text-center text-black-600 font-semibold">
            {t1("dont_have_wallet", {
              walletName: walletDetail[walletConfigKeys.walletName],
            })}
            &nbsp;
            <a
              href={walletDetail[walletConfigKeys.appDownloadURI]}
              className="wallet-download"
              id="download_now"
            >
              {t1("download_now")}
            </a>
          </p>
        </div>
      </div>
    </>
  );
}
