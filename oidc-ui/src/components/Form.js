import { useEffect, useState, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import LoadingIndicator from '../common/LoadingIndicator';
import {
  configurationKeys,
  challengeFormats,
  challengeTypes,
} from '../constants/clientConstants';
import { LoadingStates as states } from '../constants/states';
import ErrorBanner from '../common/ErrorBanner';
import redirectOnError from '../helpers/redirectOnError';
import langConfigService from '../services/langConfigService';
import { JsonFormBuilder } from '@mosip/json-form-builder';
import { encodeString } from '../helpers/utils';

export default function Form({
  authService,
  openIDConnectService,
  backButtonDiv,
  secondaryHeading,
  i18nKeyPrefix1 = 'Form',
  i18nKeyPrefix2 = 'errors',
}) {
  const { t: t1, i18n } = useTranslation('translation', {
    keyPrefix: i18nKeyPrefix1,
  });

  const { t: t2 } = useTranslation('translation', {
    keyPrefix: i18nKeyPrefix2,
  });

  const [langConfig, setLangConfig] = useState(null);

  useEffect(() => {
    async function loadLangConfig() {
      try {
        const config = await langConfigService.getEnLocaleConfiguration();
        setLangConfig(config);
      } catch (e) {
        console.error('Failed to load lang config', e);
        setLangConfig({ errors: { otp: {} } }); // Fallback to prevent crashes
      }
    }

    loadLangConfig();
  }, []);

  const formBuilderRef = useRef(null); // Reference to form instance
  const isSubmitting = useRef(false);
  const post_AuthenticateUser = authService.post_AuthenticateUser;
  const buildRedirectParams = authService.buildRedirectParamsV2;
  const [errorBanner, setErrorBanner] = useState([]);
  const [status, setStatus] = useState(states.LOADED);

  const captchaSiteKey =
    openIDConnectService.getEsignetConfiguration(
      configurationKeys.captchaSiteKey
    ) ?? process.env.REACT_APP_CAPTCHA_SITE_KEY;

  const captchaEnableComponents =
    openIDConnectService.getEsignetConfiguration(
      configurationKeys.captchaEnableComponents
    ) ?? process.env.REACT_APP_CAPTCHA_ENABLE;

  const captchaEnableComponentsList = captchaEnableComponents
    .split(',')
    .map((x) => x.trim().toLowerCase());

  const [showCaptcha, setShowCaptcha] = useState(
    captchaEnableComponentsList.indexOf('kbi') !== -1
  );

  useEffect(() => {
    if (JsonFormBuilder && !window.__form_rendered__) {
      const formConfig = openIDConnectService.getEsignetConfiguration(
        configurationKeys.authFactorKnowledgeFieldDetails
      );

      const additionalConfig = {
        submitButton: {
          label: t1('login'),
          action: handleSubmit,
        },
        recaptcha: {
          siteKey: captchaSiteKey,
          enabled: showCaptcha,
          language: i18n.language,
        },
        language: {
          currentLanguage: i18n.language,
          defaultLanguage: window._env_.DEFAULT_LANG,
        },
      };

      const form = JsonFormBuilder(
        formConfig,
        'form-container',
        additionalConfig
      );
      form.render();
      formBuilderRef.current = form; // Save the form instance to the ref
      window.__form_rendered__ = true;
    } else if (!JsonFormBuilder) {
      console.error('JsonFormBuilder not loaded');
    }

    // Cleanup on unmount
    return () => {
      window.__form_rendered__ = false;
      formBuilderRef.current = null;
      const container = document.getElementById('form-container');
      if (container) container.innerHTML = ''; // optional: clean old content
    };
  }, []);

  useEffect(() => {
    formBuilderRef.current?.updateLanguage(i18n.language, t1('login'));
  }, [i18n.language]);

  const navigate = useNavigate();

  const handleSubmit = async () => {
    if (isSubmitting.current) return; // prevent multiple calls
    isSubmitting.current = true;

    const formData = formBuilderRef.current?.getFormData();

    await authenticateUser(formData);

    // Reset after authentication is done
    isSubmitting.current = false;
  };

  //Handle Login API Integration here
  const authenticateUser = async (formData) => {
    try {
      const { recaptchaToken, ...filtered } = formData;
      let transactionId = openIDConnectService.getTransactionId();
      let uin =
        formData[
          `${openIDConnectService.getEsignetConfiguration(
            configurationKeys.authFactorKnowledgeIndividualIdField
          )}`
        ];
      let challenge = encodeString(JSON.stringify(filtered));
      let challengeType = challengeTypes.kbi;
      let challengeFormat = challengeFormats.kbi;
      let challengeList = [
        {
          authFactorType: challengeType,
          challenge: challenge,
          format: challengeFormat,
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

      if (errors !== null && errors.length > 0) {
        let errorCodeCondition =
          langConfig.errors.kbi[errors[0].errorCode] !== undefined &&
          langConfig.errors.kbi[errors[0].errorCode] !== null;

        if (errorCodeCondition) {
          setErrorBanner({
            errorCode: `kbi.${errors[0].errorCode}`,
            show: true,
          });
        } else if (errors[0].errorCode === 'invalid_transaction') {
          redirectOnError(errors[0].errorCode, t2(`${errors[0].errorCode}`));
        } else {
          setErrorBanner({
            errorCode: `${errors[0].errorCode}`,
            show: true,
          });
        }
        formBuilderRef.current?.updateLanguage(i18n.language, t1('login'));
        return;
      } else {
        setErrorBanner(null);
        let params = buildRedirectParams({
          nonce: openIDConnectService.getNonce(),
          state: openIDConnectService.getState(),
          oauthResponse: openIDConnectService.getOAuthDetails(),
          consentAction: response.consentAction,
          ui_locales: i18n.language,
        });

        navigate(process.env.PUBLIC_URL + '/claim-details' + params, {
          replace: true,
        });
      }
    } catch (error) {
      setErrorBanner({
        errorCode: 'kbi.auth_failed',
        show: true,
      });
      formBuilderRef.current?.updateLanguage(i18n.language, t1('login'));
      setStatus(states.ERROR);
    }
  };

  useEffect(() => {
    let loadComponent = async () => {
      i18n.on('languageChanged', () => {
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

      <div id="form-container" className="kbi_form"></div>

      {status === states.LOADING && (
        <div className="mt-2">
          <LoadingIndicator size="medium" message="authenticating_msg" />
        </div>
      )}
    </>
  );
}
