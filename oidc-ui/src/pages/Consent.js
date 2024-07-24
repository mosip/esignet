import React from "react";
import Consent from "../components/Consent";
import authService from "../services/authService";
import { Buffer } from "buffer";
import { useLocation, useSearchParams } from "react-router-dom";
import openIDConnectService from "../services/openIDConnectService";
import DefaultError from "../components/DefaultError";
import sha256 from "crypto-js/sha256";
import Base64 from "crypto-js/enc-base64";

export default function ConsentPage() {
  const [searchParams, setSearchParams] = useSearchParams();

  const location = useLocation();

  let decodeOAuth = Buffer.from(location.hash ?? "", "base64")?.toString();
  let nonce = searchParams.get("nonce");
  let state = searchParams.get("state");
  const consentAction = searchParams.get("consentAction");
  const authTime = searchParams.get("authenticationTime");
  const key = searchParams.get("key");
  const urlInfo = localStorage.getItem(key);

  // Create a URL object using the URL info
  const urlInfoObj = new URL(
    `${window.location.origin}${window.location.pathname}?${urlInfo}`
  );

  // Initialize URLSearchParams with the search part of the URL object
  const urlInfoParams = new URLSearchParams(urlInfoObj.search);

  // Initialize URLSearchParams with the search part of the current window location
  const params = new URLSearchParams(window.location.search);

  // Function to get the hash of OAuth details
  const getOauthDetailsHash = async (value) => {
    let sha256Hash = sha256(JSON.stringify(value));
    let hashB64 = Base64.stringify(sha256Hash)
      .replace(/=+$/, "")
      .replace(/\+/g, "-")
      .replace(/\//g, "_");
    return hashB64;
  };

  if (key && urlInfo) {
    // Parse the hash from the URL info
    const hash = JSON.parse(atob(urlInfo.split("#")[1]));

    // Destructure the transactionId from the hash
    const { transactionId } = hash;

    const resume = async (hash) => {
      // Get the OAuth details hash
      const oAuthDetailsHash = await getOauthDetailsHash(hash);

      // Initialize the openIDConnectService
      const oidcService = new openIDConnectService(
        hash,
        urlInfoParams.get("nonce"),
        urlInfoParams.get("state")
      );

      // Get the redirect URI from the openIDConnectService
      const redirect_uri = oidcService.getRedirectUri();

      // Initialize the authService with the openIDConnectService
      const authServices = new authService(oidcService);

      const { response, errors } = await authServices.resume(
        transactionId,
        params.has("error"),
        oAuthDetailsHash
      );

      window.onbeforeunload = null;

      // log to check the response for the dev testing
      console.log(response);

      if (!errors.length) {
        // Set the authenticationTime parameter
        urlInfoParams.set("authenticationTime", Math.floor(Date.now() / 1000));

        // Update the search part of the URL object
        urlInfoObj.search = urlInfoParams.toString();

        // Redirect to the updated URL (load the consent screen)
        window.location.replace(urlInfoObj.toString());
      } else {
        urlInfoParams.set("error_description", errors[0].errorCode);
        urlInfoParams.set("error", errors[0].errorCode);

        // Redirect to the redirect URI with the error parameters (load the relying party screen)
        window.location.replace(`${redirect_uri}?${urlInfoParams}`);
      }
    };

    resume(hash);
  }

  let parsedOauth = null;
  try {
    if (urlInfo === null) {
      parsedOauth = JSON.parse(decodeOAuth);
    }
  } catch (error) {
    return (
      <DefaultError
        backgroundImgPath="images/illustration_one.png"
        errorCode={"unauthorized_access"}
      />
    );
  }

  const oidcService = new openIDConnectService(parsedOauth, nonce, state);

  return (
    <Consent
      backgroundImgPath="images/illustration_one.png"
      authService={new authService(oidcService)}
      openIDConnectService={oidcService}
      consentAction={consentAction}
      authTime={authTime}
    />
  );
}
