import React from "react";
import Consent from "../components/Consent";
import authService from "../services/authService";
import { Buffer } from "buffer";
import { useSearchParams } from "react-router-dom";
import openIDConnectService from "../services/openIDConnectService";

export default function ConsentPage() {

  const [searchParams, setSearchParams] = useSearchParams();
  let response = searchParams.get("response")
  let nonce = searchParams.get("nonce")
  let state = searchParams.get("state")
  var decodeOAuth = Buffer.from(response, 'base64')?.toString();
  const oidcService = new openIDConnectService(JSON.parse(decodeOAuth), nonce, state);

  return (
    <Consent
      authService={new authService(oidcService)}
      openIDConnectService={oidcService}
    />
  );
}
