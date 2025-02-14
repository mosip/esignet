import React, { useEffect, useState } from "react";
import Otp from "../components/Otp";
import Pin from "../components/Pin";
import { generateFieldData } from "../constants/formFields";
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
import Form from "../components/Form";
import { purposeObj } from "../constants/clientConstants";

function InitiateL1Biometrics(openIDConnectService, backButtonDiv) {
  return React.createElement(L1Biometrics, {
    param: generateFieldData(validAuthFactors.BIO, openIDConnectService),
    authService: new authService(openIDConnectService),
    localStorageService: localStorageService,
    openIDConnectService: openIDConnectService,
    sbiService: new sbiService(openIDConnectService),
    backButtonDiv: backButtonDiv,
  });
}

function InitiatePin(openIDConnectService, backButtonDiv) {
  return React.createElement(Pin, {
    param: generateFieldData(validAuthFactors.PIN, openIDConnectService),
    authService: new authService(openIDConnectService),
    openIDConnectService: openIDConnectService,
    backButtonDiv: backButtonDiv,
  });
}

function InitiatePassword(openIDConnectService, backButtonDiv) {
  return React.createElement(Password, {
    param: generateFieldData(validAuthFactors.PWD, openIDConnectService),
    authService: new authService(openIDConnectService),
    openIDConnectService: openIDConnectService,
    backButtonDiv: backButtonDiv,
  });
}

function InitiateOtp(openIDConnectService, backButtonDiv) {
  return React.createElement(Otp, {
    param: generateFieldData(validAuthFactors.OTP, openIDConnectService),
    authService: new authService(openIDConnectService),
    openIDConnectService: openIDConnectService,
    backButtonDiv: backButtonDiv,
  });
}

function InitiateForm(openIDConnectService, backButtonDiv) {
  return React.createElement(Form, {
    param: generateFieldData(validAuthFactors.KBI, openIDConnectService),
    authService: new authService(openIDConnectService),
    openIDConnectService: openIDConnectService,
    backButtonDiv: backButtonDiv,
  });
}

function InitiateSignInOptions(
  handleSignInOptionClick,
  openIDConnectService,
  icons
) {
  return React.createElement(SignInOptions, {
    openIDConnectService: openIDConnectService,
    handleSignInOptionClick: handleSignInOptionClick,
    icons: icons,
  });
}

function InitiateLinkedWallet(authFactor, openIDConnectService, backButtonDiv) {
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

function createDynamicLoginElements(authFactor, oidcService, backButtonDiv) {
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

  if (authFactorType === validAuthFactors.KBI) {
    return InitiateForm(oidcService, backButtonDiv);
  }

  if (authFactorType === validAuthFactors.WLA) {
    return InitiateLinkedWallet(authFactor, oidcService, backButtonDiv);
  }

  // default element
  return React.createElement(Otp);
}

export default function LoginPage({ i18nKeyPrefix = "header" }) {
  const { t, i18n } = useTranslation("translation", {
    keyPrefix: i18nKeyPrefix,
  });
  const [compToShow, setCompToShow] = useState(null);
  const [clientLogoURL, setClientLogoURL] = useState(null);
  const [clientName, setClientName] = useState(null);
  const [authFactorType, setAuthFactorType] = useState(null);
  const [heading, setHeading] = useState(purposeObj.login);
  const [searchParams] = useSearchParams();
  const location = useLocation();

  var decodeOAuth = Buffer.from(location.hash ?? "", "base64")?.toString();
  var nonce = searchParams.get("nonce");
  var state = searchParams.get("state");
  var icons;

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

  const handleSignInOptionClick = (authFactor, icon) => {
    icons = icon;
    setAuthFactorType(authFactor.type);
    //TODO handle multifactor auth
    setCompToShow(
      createDynamicLoginElements(
        authFactor,
        oidcService,
        backButtonDiv(
          oidcService.getAuthFactorList().length > 1
            ? handleBackButtonClick
            : null
        )
      )
    );
  };

  const handleBackButtonClick = () => {
    setAuthFactorType(null);
    setCompToShow(
      InitiateSignInOptions(handleSignInOptionClick, oidcService, icons)
    );
  };

  const backButtonDiv = (handleBackButtonClick) => {
    return (
      handleBackButtonClick && (
        <div className="inline w-max mb-1">
          <button
            id="back-button"
            onClick={() => handleBackButtonClick()}
            className="back-button-color text-2xl font-semibold justify-left rtl:rotate-180 relative top-[2px]"
          >
            <svg
              width="15"
              height="13"
              viewBox="0 0 15 13"
              fill="none"
              xmlns="http://www.w3.org/2000/svg"
              className="mr-2 relative top-[2px]"
              aria-label="left_arrow_icon"
            >
              <g clipPath="url(#clip0_171_703)">
                <path
                  d="M5.96463 12.0845L5.78413 11.8961L0.512572 6.39538L0.346802 6.2224L0.512572 6.04942L5.78413 0.548692L5.96463 0.360352L6.14513 0.548692L7.09357 1.53836L7.25934 1.71134L7.09357 1.88432L3.83751 5.28193H14.155H14.405V5.53193V6.91287V7.16287H14.155H3.83751L7.09357 10.5605L7.25934 10.7335L7.09357 10.9064L6.14513 11.8961L5.96463 12.0845Z"
                  fill="currentColor"
                />
                <path
                  d="M5.96458 11.7231L6.91302 10.7335L3.2516 6.91286H14.1549V5.53192H3.2516L6.91302 1.71133L5.96458 0.721663L0.693018 6.22239L5.96458 11.7231ZM5.96458 12.4458L0.000488281 6.22239L5.96458 -0.000976562L7.60555 1.71133L4.4233 5.03192H14.6549V7.41286H4.4233L7.60555 10.7335L5.96458 12.4458Z"
                  fill="currentColor"
                />
              </g>
              <defs>
                <clipPath id="clip0_171_703">
                  <rect width="14.654" height="12.447" fill="white" />
                </clipPath>
              </defs>
            </svg>
          </button>
        </div>
      )
    );
  };

  const loadComponent = () => {
    let oAuthDetailResponse = oidcService.getOAuthDetails();
    // set dynamic heading according the purpose from oauth details
    // otherwise default heading will be login
    if (oAuthDetailResponse?.purpose && (oAuthDetailResponse.purpose in purposeObj)) {
      setHeading(purposeObj[oAuthDetailResponse.purpose]);
    }
    setClientLogoURL(oAuthDetailResponse?.logoUrl);
    setClientName(oAuthDetailResponse?.clientName);
    handleBackButtonClick();
  };

  function checkForIDT(authFactors) {
    for (const factor of authFactors) {
      if (Array.isArray(factor)) {
        if (checkForIDT(factor)) {
          return true;
        }
      } else if (factor.type === "IDT") {
        return true;
      }
    }
    return false;
  }

  return (
    <>
      {!checkForIDT(JSON.parse(decodeOAuth).authFactors) && (
        <Background
          heading={t(heading, {
            idProviderName: window._env_.DEFAULT_ID_PROVIDER_NAME,
          })}
          subheading={clientName}
          clientLogoPath={clientLogoURL}
          clientName={clientName}
          component={compToShow}
          oidcService={oidcService}
          authService={new authService(null)}
        />
      )}
    </>
  );
}
