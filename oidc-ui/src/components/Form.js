import { useEffect, useState, useRef } from "react";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import LoadingIndicator from "../common/LoadingIndicator";
import { configurationKeys } from "../constants/clientConstants";
import { LoadingStates as states } from "../constants/states";
import ErrorBanner from "../common/ErrorBanner";
import redirectOnError from "../helpers/redirectOnError";
import langConfigService from "../services/langConfigService";
import { JsonFormBuilder } from "@anushase/json-form-builder";
import { Buffer } from "buffer";

const langConfig = await langConfigService.getEnLocaleConfiguration();

export default function Form({
  authService,
  openIDConnectService,
  backButtonDiv,
  secondaryHeading,
  i18nKeyPrefix1 = "Form",
  i18nKeyPrefix2 = "errors",
}) {
  const { t: t1, i18n } = useTranslation("translation", {
    keyPrefix: i18nKeyPrefix1,
  });

  const { t: t2 } = useTranslation("translation", {
    keyPrefix: i18nKeyPrefix2,
  });

  const formBuilderRef = useRef(null); // Reference to form instance
  const isSubmitting = useRef(false);
  const post_AuthenticateUser = authService.post_AuthenticateUser;
  const buildRedirectParams = authService.buildRedirectParams;
  const [errorBanner, setErrorBanner] = useState([]);
  const [status, setStatus] = useState(states.LOADED);

  const decodedBase64 = Buffer.from(
    authService.getAuthorizeQueryParam(),
    "base64"
  ).toString();

  // Function to extract query parameters
  const getQueryParam = (url, param) => {
    const urlParams = new URLSearchParams(url);
    return urlParams.get(param);
  };

  // Fetch the value of ui_locales
  const uiLocales = getQueryParam(decodedBase64, "ui_locales");

  const captchaSiteKey =
    openIDConnectService.getEsignetConfiguration(
      configurationKeys.captchaSiteKey
    ) ?? process.env.REACT_APP_CAPTCHA_SITE_KEY;

  const captchaEnableComponents =
    openIDConnectService.getEsignetConfiguration(
      configurationKeys.captchaEnableComponents
    ) ?? process.env.REACT_APP_CAPTCHA_ENABLE;

  const captchaEnableComponentsList = captchaEnableComponents
    .split(",")
    .map((x) => x.trim().toLowerCase());

  const [showCaptcha, setShowCaptcha] = useState(
    captchaEnableComponentsList.indexOf("kbi") !== -1
  );

  useEffect(() => {
    if (JsonFormBuilder && !window.__form_rendered__) {
      // const formConfig = {
      //   schema: [
      //     {
      //       id: "individualId",
      //       controlType: "textbox",
      //       label: { en: "Policy Number" },
      //       validators: [
      //         {
      //           type: "regex",
      //           validator: "",
      //           langCode: "en",
      //           errorCode: "",
      //         },
      //       ],
      //       alignmentGroup: "groupA",
      //       cssClasses: ["classA", "classB"],
      //       required: true,
      //     },
      //     {
      //       id: "phoneNumber",
      //       controlType: "textbox",
      //       type: "simpleType",
      //       label: {
      //         en: "Phone Number",
      //         km: "លេខទូរស័ព្ទ",
      //         ar: "رقم التليفون",
      //       },
      //       validators: [
      //         {
      //           type: "regex",
      //           validator: "",
      //           langCode: "en",
      //           errorCode: "",
      //         },
      //       ],
      //       alignmentGroup: "groupB",
      //       cssClasses: ["classA", "classB"],
      //       required: true,
      //     },
      //     {
      //       id: "birthDate",
      //       controlType: "date",
      //       type: "date",
      //       label: { en: "Birth Date", km: "ថ្ងៃខែឆ្នាំកំណើត" },
      //       alignmentGroup: "groupC",
      //       cssClasses: ["classA", "classB"],
      //       required: true,
      //     },
      //   ],
      //   mandatoryLanguages: ["en"],
      //   optionalLanguages: ["km", "ar"],
      // };

      const formConfig = openIDConnectService.getEsignetConfiguration(
        configurationKeys.authFactorKnowledgeFieldDetails
      );

      const additionalConfig = {
        submitButton: {
          label: t1("login"),
          action: handleSubmit,
        },
        recaptcha: {
          siteKey: captchaSiteKey,
          enabled: showCaptcha,
          language: i18n.language,
        },
        language: {
          currentLanguage: uiLocales,
          defaultLanguage: window._env_.DEFAULT_LANG,
        },
      };

      const form = JsonFormBuilder(
        formConfig,
        "form-container",
        additionalConfig
      );
      form.render();
      formBuilderRef.current = form; // Save the form instance to the ref
      window.__form_rendered__ = true;
    } else if (!JsonFormBuilder) {
      console.error("JsonFormBuilder not loaded");
    }

    // Cleanup on unmount
    return () => {
      window.__form_rendered__ = false;
      formBuilderRef.current = null;
      const container = document.getElementById("form-container");
      if (container) container.innerHTML = ""; // optional: clean old content
    };
  }, []);

  useEffect(() => {
    formBuilderRef.current?.updateLanguage(i18n.language);
  }, [i18n.language]);

  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    if (isSubmitting.current) return; // prevent multiple calls
    isSubmitting.current = true;

    const formData = formBuilderRef.current?.getFormData();
    console.log("Form Data:", formData); // for dev testing

    await authenticateUser(formData);

    // Reset after authentication is done
    isSubmitting.current = false;
  };

  //Handle Login API Integration here
  const authenticateUser = async (formData) => {
    try {
      const { individualId, recaptchaToken, ...filtered } = formData;
      let transactionId = openIDConnectService.getTransactionId();
      let uin =
        formData[
          `${openIDConnectService.getEsignetConfiguration(
            configurationKeys.authFactorKnowledgeIndividualIdField
          )}`
        ];
      // let uin = formData["individualId"];
      let challenge = btoa(JSON.stringify(filtered));

      let challengeList = [
        {
          authFactorType: "KBI",
          challenge: challenge,
          format: "base64url-encoded-json",
        },
      ];

      setStatus(states.LOADING);

      const authenticateResponse = await post_AuthenticateUser(
        transactionId,
        uin,
        challengeList,
        recaptchaToken
      );

      setStatus(states.LOADED);

      const { response, errors } = authenticateResponse;

      if (errors != null && errors.length > 0) {
        let errorCodeCondition =
          langConfig.errors.otp[errors[0].errorCode] !== undefined &&
          langConfig.errors.kbi[errors[0].errorCode] !== null;

        if (errorCodeCondition) {
          setErrorBanner({
            errorCode: `kbi.${errors[0].errorCode}`,
            show: true,
          });
        } else if (errors[0].errorCode === "invalid_transaction") {
          redirectOnError(errors[0].errorCode, t2(`${errors[0].errorCode}`));
        } else {
          setErrorBanner({
            errorCode: `${errors[0].errorCode}`,
            show: true,
          });
        }
        return;
      } else {
        setErrorBanner(null);
        let nonce = openIDConnectService.getNonce();
        let state = openIDConnectService.getState();

        let params = buildRedirectParams(
          nonce,
          state,
          openIDConnectService.getOAuthDetails(),
          response.consentAction
        );

        navigate(process.env.PUBLIC_URL + "/claim-details" + params, {
          replace: true,
        });
      }
    } catch (error) {
      setErrorBanner({
        errorCode: "kbi.auth_failed",
        show: true,
      });
      setStatus(states.ERROR);
    }
  };

  useEffect(() => {
    let loadComponent = async () => {
      i18n.on("languageChanged", () => {
        if (showCaptcha) {
          setShowCaptcha(true);
        }
      });
    };

    loadComponent();
  }, []);

  const onCloseHandle = () => {
    setErrorBanner(null);
  };

  return (
    <>
      <div className="flex items-center">
        {backButtonDiv}
        <div className="inline mx-2 font-semibold my-3">
          {t1(secondaryHeading)}
        </div>
      </div>

      {errorBanner !== null && errorBanner.show && (
        <div className="mb-4">
          <ErrorBanner
            showBanner={errorBanner.show}
            errorCode={t2(errorBanner.errorCode)}
            onCloseHandle={onCloseHandle}
          />
        </div>
      )}

      <div id="form-container"></div>

      {status === states.LOADING && (
        <div className="mt-2">
          <LoadingIndicator size="medium" message="authenticating_msg" />
        </div>
      )}
    </>
  );
}
