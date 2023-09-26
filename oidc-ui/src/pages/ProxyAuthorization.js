import React from "react";
import relyingPartyService from "../services/relyingPartyService";
import ProxyAuthorization from "../components/ProxyAuthorization";

export default function ProxyAuthorizationPage() {
  return <ProxyAuthorization relyingPartyService={relyingPartyService} />;
}
