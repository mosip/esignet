import React from "react";
import Consent from "../components/Consent";
import authService from "../services/authService";
import { Buffer } from "buffer";
import { useLocation, useSearchParams } from "react-router-dom";
import openIDConnectService from "../services/openIDConnectService";
import ErrorIndicator from "../common/ErrorIndicator";

export default function ConsentPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const location = useLocation();

  let decodeOAuth = Buffer.from(location.hash ?? "", "base64")?.toString();
  let nonce = searchParams.get("nonce");
  let state = searchParams.get("state");

  let parsedOauth = null;
  try {
    parsedOauth = JSON.parse(decodeOAuth);
  } catch (error) {
    return (
      //TODO naviagte to default error page
      <div className="flex h-5/6 items-center justify-center py-12 px-4 sm:px-6 lg:px-8">
        <div className="max-w-md w-full space-y-8">
          <ErrorIndicator errorCode={"parsing_error_msg"} />
        </div>
      </div>
    );
  }

  const oidcService = new openIDConnectService(parsedOauth, nonce, state);

  return (
    <Consent
      authService={new authService(oidcService)}
      openIDConnectService={oidcService}
    />
  );
}
