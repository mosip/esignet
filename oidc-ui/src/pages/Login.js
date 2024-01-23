import React, { useEffect, useState } from "react";
import Otp from "../components/Otp";
import Pin from "../components/Pin";
import {
  otpFields,
  pinFields,
  bioLoginFields,
  passwordFields,
} from "../constants/formFields";
import L1Biometrics from "../components/L1Biometrics";
import { useTranslation } from "react-i18next";
import authService from "../services/authService";
import localStorageService from "../services/local-storageService";
import sbiService from "../services/sbiService";
import Background from "../components/Background";
import SignInOptions from "../components/SignInOptions";
import { validAuthFactors } from "../constants/clientConstants";
import linkAuthService from "../services/linkAuthService";
import LoginQRCode from "../components/LoginQRCode";
import { useLocation, useSearchParams } from "react-router-dom";
import { Buffer } from "buffer";
import openIDConnectService from "../services/openIDConnectService";
import DefaultError from "../components/DefaultError";
import Password from "../components/Password";

function InitiateL1Biometrics(openIDConnectService, backButtonDiv) {
  return React.createElement(L1Biometrics, {
    param: bioLoginFields,
    authService: new authService(openIDConnectService),
    localStorageService: localStorageService,
    openIDConnectService: openIDConnectService,
    sbiService: new sbiService(openIDConnectService),
    backButtonDiv: backButtonDiv,
  });
}

function InitiatePin(openIDConnectService, backButtonDiv) {
  return React.createElement(Pin, {
    param: pinFields,
    authService: new authService(openIDConnectService),
    openIDConnectService: openIDConnectService,
    backButtonDiv: backButtonDiv,
  });
}

function InitiatePassword(openIDConnectService, backButtonDiv) {
  return React.createElement(Password, {
    param: passwordFields,
    authService: new authService(openIDConnectService),
    openIDConnectService: openIDConnectService,
    backButtonDiv: backButtonDiv,
  });
}

function InitiateOtp(openIDConnectService, backButtonDiv) {
  return React.createElement(Otp, {
    param: otpFields,
    authService: new authService(openIDConnectService),
    openIDConnectService: openIDConnectService,
    backButtonDiv: backButtonDiv,
  });
}

function InitiateSignInOptions(handleSignInOptionClick, openIDConnectService) {
  return React.createElement(SignInOptions, {
    openIDConnectService: openIDConnectService,
    handleSignInOptionClick: handleSignInOptionClick,
  });
}

function InitiateLinkedWallet(
  authFactor,
  openIDConnectService,
  backButtonDiv
) {
  return React.createElement(LoginQRCode, {
    walletDetail: authFactor,
    openIDConnectService: openIDConnectService,
    linkAuthService: new linkAuthService(openIDConnectService),
    backButtonDiv: backButtonDiv,
  });
}

function InitiateInvalidAuthFactor(errorMsg) {
  return React.createElement(() => <div>{errorMsg}</div>);
}

function createDynamicLoginElements(
  authFactor,
  oidcService,
  backButtonDiv
) {
  const authFactorType = authFactor.type;
  if (typeof authFactorType === "undefined") {
    return InitiateInvalidAuthFactor(
      "The component " + { authFactorType } + " has not been created yet."
    );
  }

  if (authFactorType === validAuthFactors.OTP) {
    return InitiateOtp(oidcService, backButtonDiv);
  }

  if (authFactorType === validAuthFactors.PIN) {
    return InitiatePin(oidcService, backButtonDiv);
  }

  if (authFactorType === validAuthFactors.BIO) {
    return InitiateL1Biometrics(oidcService, backButtonDiv);
  }

  if (authFactorType === validAuthFactors.PWD) {
    return InitiatePassword(oidcService, backButtonDiv);
  }

  if (authFactorType === validAuthFactors.WLA) {
    return InitiateLinkedWallet(authFactor, oidcService, backButtonDiv);
  }

  // default element
  return React.createElement(Otp);
}

export default function LoginPage({ i18nKeyPrefix = "header" }) {
  const { t } = useTranslation("translation", { keyPrefix: i18nKeyPrefix });
  const [compToShow, setCompToShow] = useState(null);
  const [clientLogoURL, setClientLogoURL] = useState(null);
  const [clientName, setClientName] = useState(null);
  const [searchParams, setSearchParams] = useSearchParams();
  const location = useLocation();

  var decodeOAuth = Buffer.from(location.hash ?? "", "base64")?.toString();
  var nonce = searchParams.get("nonce");
  var state = searchParams.get("state");

  useEffect(() => {
    if (!decodeOAuth) {
      return;
    }
    loadComponent();
  }, []);

  let parsedOauth = null;

  try {
    parsedOauth = JSON.parse(decodeOAuth);
  } catch (error) {
    return (
      <DefaultError
        backgroundImgPath="images/illustration_one.png"
        errorCode={"parsing_error_msg"}
      />
    );
  }

  const oidcService = new openIDConnectService(parsedOauth, nonce, state);

  const handleSignInOptionClick = (authFactor) => {
    //TODO handle multifactor auth
    setCompToShow(
      createDynamicLoginElements(
        authFactor,
        oidcService,
        backButtonDiv(oidcService.getAuthFactorList().length > 1 ? handleBackButtonClick : null)
      )
    );
  };

  const handleBackButtonClick = () => {
    setCompToShow(InitiateSignInOptions(handleSignInOptionClick, oidcService));
  };

  const backButtonDiv = (handleBackButtonClick) => {
    return (
      handleBackButtonClick && (
        <div className="h-6 items-center text-center flex items-start">
          <button
            onClick={() => handleBackButtonClick()}
            className="text-sky-600 text-2xl font-semibold justify-left rtl:rotate-180"
          >
            &#8592;
          </button>
        </div>
      )
    );
  };

  const loadComponent = () => {
    let oAuthDetailResponse = oidcService.getOAuthDetails();
    setClientLogoURL(oAuthDetailResponse?.logoUrl);
    setClientName(oAuthDetailResponse?.clientName);

    handleBackButtonClick();
  };

  return (
    <>
      <Background
        heading={t("login_heading", {
          idProviderName: window._env_.DEFAULT_ID_PROVIDER_NAME,
        })}
        subheading={t("login_subheading")}
        clientLogoPath={clientLogoURL}
        clientName={clientName}
        component={compToShow}
        oidcService={oidcService}
        authService={new authService(null)}
      />
    </>
  );
}
