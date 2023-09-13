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

function InitiateL1Biometrics(openIDConnectService, handleBackButtonClick) {
  return React.createElement(L1Biometrics, {
    param: bioLoginFields,
    authService: new authService(openIDConnectService),
    localStorageService: localStorageService,
    openIDConnectService: openIDConnectService,
    sbiService: new sbiService(openIDConnectService),
    handleBackButtonClick: handleBackButtonClick,
  });
}

function InitiatePin(openIDConnectService, handleBackButtonClick) {
  return React.createElement(Pin, {
    param: pinFields,
    authService: new authService(openIDConnectService),
    openIDConnectService: openIDConnectService,
    handleBackButtonClick: handleBackButtonClick,
  });
}

function InitiatePassword(openIDConnectService, handleBackButtonClick) {
  return React.createElement(Password, {
    param: passwordFields,
    authService: new authService(openIDConnectService),
    openIDConnectService: openIDConnectService,
    handleBackButtonClick: handleBackButtonClick,
  });
}

function InitiateOtp(openIDConnectService, handleBackButtonClick) {
  return React.createElement(Otp, {
    param: otpFields,
    authService: new authService(openIDConnectService),
    openIDConnectService: openIDConnectService,
    handleBackButtonClick: handleBackButtonClick,
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
  handleBackButtonClick
) {
  return React.createElement(LoginQRCode, {
    walletDetail: authFactor,
    openIDConnectService: openIDConnectService,
    linkAuthService: new linkAuthService(openIDConnectService),
    handleBackButtonClick: handleBackButtonClick,
  });
}

function InitiateInvalidAuthFactor(errorMsg) {
  return React.createElement(() => <div>{errorMsg}</div>);
}

function createDynamicLoginElements(
  authFactor,
  oidcService,
  handleBackButtonClick
) {
  const authFactorType = authFactor.type;
  if (typeof authFactorType === "undefined") {
    return InitiateInvalidAuthFactor(
      "The component " + { authFactorType } + " has not been created yet."
    );
  }

  if (authFactorType === validAuthFactors.OTP) {
    return InitiateOtp(oidcService, handleBackButtonClick);
  }

  if (authFactorType === validAuthFactors.PIN) {
    return InitiatePin(oidcService, handleBackButtonClick);
  }

  if (authFactorType === validAuthFactors.BIO) {
    return InitiateL1Biometrics(oidcService, handleBackButtonClick);
  }

  if (authFactorType === validAuthFactors.PWD) {
    return InitiatePassword(oidcService, handleBackButtonClick);
  }

  if (authFactorType === validAuthFactors.WLA) {
    return InitiateLinkedWallet(
      authFactor,
      oidcService,
      handleBackButtonClick
    );
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
        handleBackButtonClick
      )
    );
  };

  const handleBackButtonClick = () => {
    setCompToShow(InitiateSignInOptions(handleSignInOptionClick, oidcService));
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
        heading={t("login_heading")}
        logoPath="logo.png"
        clientLogoPath={clientLogoURL}
        clientName={clientName}
        backgroundImgPath="images/illustration_one.png"
        component={compToShow}
      />
    </>
  );
}
