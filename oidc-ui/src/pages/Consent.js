import React from "react";
import Consent from "../components/Consent";
import authService from "../services/authService";
import { Buffer } from "buffer";
import { useLocation, useSearchParams } from "react-router-dom";
import openIDConnectService from "../services/openIDConnectService";
import DefaultError from "../components/DefaultError";

export default function ConsentPage() {
  const [searchParams, setSearchParams] = useSearchParams();

  const location = useLocation();

  let decodeOAuth = Buffer.from(location.hash ?? "", "base64")?.toString();
  let nonce = searchParams.get("nonce");
  let state = searchParams.get("state");
  const consentAction = searchParams.get("consentAction");
  const authTime = searchParams.get("authenticationTime");


  let parsedOauth = null;
  try {
    parsedOauth = JSON.parse(decodeOAuth);
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
