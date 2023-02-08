import React, { useEffect, useState } from "react";
import Otp from "../components/Otp";
import Pin from "../components/Pin";
import { otpFields, pinFields, bioLoginFields } from "../constants/formFields";
import L1Biometrics from "../components/L1Biometrics";
import { useTranslation } from "react-i18next";
import authService from "../services/authService";
import localStorageService from "../services/local-storageService";
import sbiService from "../services/sbiService";
import Background from "../components/Background";
import SignInOptions from "../components/SignInOptions";
import {
  configurationKeys,
  validAuthFactors,
} from "../constants/clientConstants";
import linkAuthService from "../services/linkAuthService";
import LoginQRCode from "../components/LoginQRCode";
import { useLocation, useSearchParams } from "react-router-dom";
import { Buffer } from "buffer";
import openIDConnectService from "../services/openIDConnectService";
import ErrorIndicator from "../common/ErrorIndicator";

//authFactorComponentMapping
const comp = {
  PIN: Pin,
  OTP: Otp,
  BIO: L1Biometrics,
};

function InitiateL1Biometrics(openIDConnectService) {
  return React.createElement(L1Biometrics, {
    param: bioLoginFields,
    authService: new authService(openIDConnectService),
    localStorageService: localStorageService,
    openIDConnectService: openIDConnectService,
    sbiService: new sbiService(openIDConnectService),
  });
}

function InitiatePin(openIDConnectService) {
  return React.createElement(Pin, {
    param: pinFields,
    authService: new authService(openIDConnectService),
    openIDConnectService: openIDConnectService,
  });
}

function InitiateOtp(openIDConnectService) {
  return React.createElement(Otp, {
    param: otpFields,
    authService: new authService(openIDConnectService),
    openIDConnectService: openIDConnectService,
  });
}

function InitiateSignInOptions(handleSignInOptionClick, openIDConnectService) {
  return React.createElement(SignInOptions, {
    openIDConnectService: openIDConnectService,
    handleSignInOptionClick: handleSignInOptionClick,
  });
}

function InitiateLinkedWallet(openIDConnectService) {
  return React.createElement(LoginQRCode, {
    openIDConnectService: openIDConnectService,
    linkAuthService: new linkAuthService(openIDConnectService),
  });
}

function InitiateInvalidAuthFactor(errorMsg) {
  return React.createElement(() => <div>{errorMsg}</div>);
}

function createDynamicLoginElements(inst, oidcService) {
  if (typeof comp[inst] === "undefined") {
    return InitiateInvalidAuthFactor(
      "The component " + { inst } + " has not been created yet."
    );
  }

  if (comp[inst] === Otp) {
    return InitiateOtp(oidcService);
  }

  if (comp[inst] === Pin) {
    return InitiatePin(oidcService);
  }

  if (comp[inst] === L1Biometrics) {
    return InitiateL1Biometrics(oidcService);
  }

  return React.createElement(comp[inst]);
}

export default function LoginPage({ i18nKeyPrefix = "header" }) {
  const { t } = useTranslation("translation", { keyPrefix: i18nKeyPrefix });
  const [compToShow, setCompToShow] = useState(null);
  const [showMoreOption, setShowMoreOption] = useState(false);
  const [clientLogoURL, setClientLogoURL] = useState(null);
  const [clientName, setClientName] = useState(null);
  const [appDownloadURI, setAppDownloadURI] = useState(null);
  const [searchParams, setSearchParams] = useSearchParams();
  const location = useLocation();

  var decodeOAuth = Buffer.from(location.hash ?? "", "base64")?.toString();
  var nonce = searchParams.get("nonce");
  var state = searchParams.get("state");

  useEffect(() => {
    loadComponent();
  }, []);

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

  let value =
    oidcService.getEsignetConfiguration(
      configurationKeys.signInWithQRCodeEnable
    ) ?? process.env.REACT_APP_QRCODE_ENABLE;

  const qrCodeEnable = value?.toString().toLowerCase() === "true";

  const handleSignInOptionClick = (authFactor) => {
    //TODO handle multifactor auth
    setShowMoreOption(true);
    setCompToShow(createDynamicLoginElements(authFactor[0].type, oidcService));
  };

  const handleMoreWaysToSignIn = () => {
    setShowMoreOption(false);
    setCompToShow(InitiateSignInOptions(handleSignInOptionClick, oidcService));
  };

  const loadComponent = () => {
    setAppDownloadURI(
      oidcService.getEsignetConfiguration(configurationKeys.appDownloadURI) ??
        process.env.REACT_APP_QRCODE_APP_DOWNLOAD_URI
    );

    let oAuthDetailResponse = oidcService.getOAuthDetails();

    try {
      setClientLogoURL(oAuthDetailResponse?.logoUrl);
      setClientName(oAuthDetailResponse?.clientName);
      let authFactors = oAuthDetailResponse?.authFactors;
      let validComponents = [];

      //checking for valid auth factors
      authFactors.forEach((authFactor) => {
        if (validAuthFactors[authFactor[0].type]) {
          validComponents.push(authFactor);
        }
      });

      let firstLoginOption = validComponents[0];
      let authFactor = firstLoginOption[0].type;
      setShowMoreOption(validComponents.length > 1);
      setCompToShow(createDynamicLoginElements(authFactor, oidcService));
    } catch (error) {
      setShowMoreOption(false);
      setCompToShow(InitiateInvalidAuthFactor(t("invalid_auth_factor")));
    }
  };

  return (
    <>
      <Background
        heading={t("login_heading")}
        logoPath="logo.png"
        clientLogoPath={clientLogoURL}
        clientName={clientName}
        backgroundImgPath="images/illustration_one.png"
        component={compToShow}
        handleMoreWaysToSignIn={handleMoreWaysToSignIn}
        showMoreOption={showMoreOption}
        linkedWalletComp={InitiateLinkedWallet(oidcService)}
        appDownloadURI={appDownloadURI}
        qrCodeEnable={qrCodeEnable}
      />
    </>
  );
}
