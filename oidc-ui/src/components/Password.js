import { useEffect, useState, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import LoadingIndicator from '../common/LoadingIndicator';
import {
  buttonTypes,
  challengeFormats,
  challengeTypes,
  configurationKeys,
} from '../constants/clientConstants';
import { passwordFields } from '../constants/formFields';
import { LoadingStates as states } from '../constants/states';
import FormAction from './FormAction';
import InputWithImage from './InputWithImage';
import ReCAPTCHA from 'react-google-recaptcha';
import ErrorBanner from '../common/ErrorBanner';
import langConfigService from '../services/langConfigService';
import redirectOnError from '../helpers/redirectOnError';
import LoginIDOptions from './LoginIDOptions';
import InputWithPrefix from './InputWithPrefix';
import { checkConfigProperty } from '../helpers/utils';

const fields = passwordFields;
let fieldsState = {};
fields.forEach((field) => (fieldsState['Password_' + field.id] = ''));

export default function Password({
  param,
  authService,
  openIDConnectService,
  backButtonDiv,
  secondaryHeading,
  i18nKeyPrefix1 = 'password',
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

  const inputCustomClass =
    'h-10 border border-input bg-transparent px-3 py-2 text-sm ring-offset-background file:border-0 file:bg-transparent file:text-sm file:font-medium placeholder:text-[hsla(0, 0%, 51%)] focus-visible:outline-none disabled:cursor-not-allowed disabled:bg-muted-light-gray shadow-none';

  const fields = param;
  const post_AuthenticateUser = authService.post_AuthenticateUser;
  const buildRedirectParams = authService.buildRedirectParams;

  const [errorBanner, setErrorBanner] = useState(null);
  const [inputErrorBanner, setInputErrorBanner] = useState([]);
  const [status, setStatus] = useState(states.LOADED);

  const [forgotPassword, setForgotPassword] = useState(false);
  const [forgotPasswordURL, setForgotPasswordURL] = useState('');

  const [password, setPassword] = useState(null);
  const [currentLoginID, setCurrentLoginID] = useState(null);
  const [countryCode, setCountryCode] = useState(null);
  const [individualId, setIndividualId] = useState(null);
  const [isValid, setIsValid] = useState(false);
  const [isBtnDisabled, setIsBtnDisabled] = useState(true);
  const [prevLanguage, setPrevLanguage] = useState(i18n.language);

  var loginIDs = openIDConnectService.getEsignetConfiguration(
    configurationKeys.loginIdOptions
  );

  let forgotPswdConfig = openIDConnectService.getEsignetConfiguration(
    configurationKeys.forgotPswdConfig
  );

  let clientAdditionalConfig = openIDConnectService.getEsignetConfiguration(
    configurationKeys.additionalConfig
  );

  const toggleForgotPwdBanner = (exist) => {
    if (exist) {
      setForgotPassword(true);
      setForgotPasswordURL(
        forgotPswdConfig[configurationKeys.forgotPswdURL] +
          '#' +
          authService.getAuthorizeQueryParam()
      );
    } else {
      setForgotPassword(false);
    }
  };

  useEffect(() => {
    if (
      checkConfigProperty(
        clientAdditionalConfig,
        configurationKeys.forgotPswdLinkRequired
      )
    ) {
      toggleForgotPwdBanner(
        clientAdditionalConfig[configurationKeys.forgotPswdLinkRequired]
      );
    } else if (
      checkConfigProperty(forgotPswdConfig, configurationKeys.forgotPswd)
    ) {
      toggleForgotPwdBanner(forgotPswdConfig[configurationKeys.forgotPswd]);
    } else {
      setForgotPassword(false);
    }
  }, [i18n.language]);

  const bannerCloseTimer =
    openIDConnectService.getEsignetConfiguration(
      configurationKeys.bannerCloseTimer
    ) ?? '';

  const navigate = useNavigate();

  function getPropertiesForLoginID(loginID, label) {
    const { prefixes, maxLength: outerMaxLength, regex: outerRegex } = loginID;

    if (Array.isArray(prefixes) && prefixes.length > 0) {
      const prefix = prefixes.find((prefix) => prefix.label === label);
      if (prefix) {
        return {
          maxLength: prefix.maxLength || outerMaxLength || null,
          regex: prefix.regex || outerRegex || null,
        };
      }
    }

    return {
      maxLength: outerMaxLength || null,
      regex: outerRegex || null,
    };
  }

  const handleChange = (e) => {
    setIsValid(true);
    onCloseHandle();
    const idProperties = getPropertiesForLoginID(
      currentLoginID,
      e.target.name.split('_')[1]
    );
    const maxLength = idProperties.maxLength;
    const regex = idProperties.regex ? new RegExp(idProperties.regex) : null;
    const trimmedValue = e.target.value.trim();

    let newValue = trimmedValue;

    setIndividualId(newValue); // Update state with the visible valid value
    if (e.target.type === 'password') {
      setPassword(e.target.value.trim());
    } else {
      setIndividualId(newValue);
    }

    setIsBtnDisabled(
      !(
        (
          (!maxLength && !regex) || // Case 1: No maxLength, no regex
          (maxLength && !regex && newValue.length <= parseInt(maxLength)) || // Case 2: maxLength only
          (!maxLength && regex && regex.test(newValue)) || // Case 3: regex only
          (maxLength &&
            regex &&
            newValue.length <= parseInt(maxLength) &&
            regex.test(newValue))
        ) // Case 4: Both maxLength and regex
      )
    );
  };

  const handleBlur = (e) => {
    const idProperties = getPropertiesForLoginID(
      currentLoginID,
      e.target.name.split('_')[1]
    );
    const maxLength = idProperties.maxLength;
    const regex = idProperties.regex ? new RegExp(idProperties.regex) : null;
    setIsValid(
      (!maxLength || e.target.value.trim().length <= parseInt(maxLength)) &&
        (!regex || regex.test(e.target.value.trim()))
    );
  };

  const handlePasswordChange = (e) => {
    onCloseHandle();
    setPassword(e.target.value.trim());
  };

  useEffect(() => {
    if (i18n.language === prevLanguage) {
      setIndividualId(null);
      setPassword(null);
      setIsValid(false);
      setIsBtnDisabled(true);
      onCloseHandle();
    } else {
      setPrevLanguage(i18n.language);
    }
  }, [currentLoginID]);

  const handleSubmit = (e) => {
    e.preventDefault();
    authenticateUser();
  };

  const captchaEnableComponents =
    openIDConnectService.getEsignetConfiguration(
      configurationKeys.captchaEnableComponents
    ) ?? process.env.REACT_APP_CAPTCHA_ENABLE;

  const captchaEnableComponentsList = captchaEnableComponents
    .split(',')
    .map((x) => x.trim().toLowerCase());

  const [showCaptcha, setShowCaptcha] = useState(
    captchaEnableComponentsList.indexOf('pwd') !== -1
  );

  const captchaSiteKey =
    openIDConnectService.getEsignetConfiguration(
      configurationKeys.captchaSiteKey
    ) ?? process.env.REACT_APP_CAPTCHA_SITE_KEY;

  const [captchaToken, setCaptchaToken] = useState(null);
  const _reCaptchaRef = useRef(null);
  const handleCaptchaChange = (value) => {
    setCaptchaToken(value);
  };

  /**
   * Reset the captcha widget
   * & its token value
   */
  const resetCaptcha = () => {
    _reCaptchaRef.current.reset();
    setCaptchaToken(null);
  };

  //Handle Login API Integration here
  const authenticateUser = async () => {
    try {
      let transactionId = openIDConnectService.getTransactionId();

      let prefix = currentLoginID.prefixes
        ? typeof currentLoginID.prefixes === 'object'
          ? countryCode
          : currentLoginID.prefixes
        : '';
      let id = individualId;
      let postfix = currentLoginID.postfix ? currentLoginID.postfix : '';

      let ID = prefix + id + postfix;
      let challengeType = challengeTypes.pswd;
      let challenge = password;
      let challengeFormat = challengeFormats.pswd;

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
        ID,
        challengeList,
        captchaToken
      );

      setStatus(states.LOADED);

      const { response, errors } = authenticateResponse;

      if (errors !== null && errors.length > 0) {
        let errorCodeCondition =
          langConfig.errors.password[errors[0].errorCode] !== undefined &&
          langConfig.errors.password[errors[0].errorCode] !== null;

        if (errorCodeCondition) {
          setErrorBanner({
            errorCode: `password.${errors[0].errorCode}`,
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
        if (showCaptcha) {
          resetCaptcha();
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

        navigate(process.env.PUBLIC_URL + '/claim-details' + params, {
          replace: true,
        });
      }
    } catch (error) {
      setErrorBanner({
        errorCode: 'password.auth_failed',
        show: true,
      });
      setStatus(states.ERROR);
      if (showCaptcha) {
        resetCaptcha();
      }
    }
  };

  useEffect(() => {
    let loadComponent = async () => {
      i18n.on('languageChanged', () => {
        if (showCaptcha) {
          //to rerender recaptcha widget on language change
          setShowCaptcha(false);
          setTimeout(() => {
            setShowCaptcha(true);
          }, 1);
        }
      });
    };

    loadComponent();
  }, []);

  const onCloseHandle = () => {
    setErrorBanner(null);
  };

  const onBlurChange = (e, errors) => {
    let id = e.target.id;
    let tempError = inputErrorBanner.map((_) => _);
    if (errors.length > 0) {
      tempError.push(id);
    } else {
      let errorIndex = tempError.findIndex((_) => _ === id);
      if (errorIndex !== -1) {
        tempError.splice(errorIndex, 1);
      }
    }
    setInputErrorBanner(tempError);
  };

  const handleForgotPassword = () => {
    window.onbeforeunload = null;
  };

  return (
    <>
      <div className="flex items-center">
        {backButtonDiv}
        {currentLoginID && (
          <div className="inline mx-2 font-semibold my-3">
            {/*
              according to the login id option, secondary heading value will be changed
              if the login id option is single, then with secondary heading will pass a object with current id
              if the login id option is multiple, then secondary heading will be passed as it is
            */}
            {t1(
              secondaryHeading,
              loginIDs &&
                loginIDs.length === 1 && {
                  currentID: t1(loginIDs[0].id),
                }
            )}
          </div>
        )}
      </div>

      {errorBanner !== null && (
        <div className="mb-4">
          <ErrorBanner
            showBanner={errorBanner.show}
            errorCode={t2(errorBanner.errorCode)}
            onCloseHandle={onCloseHandle}
            bannerCloseTimer={bannerCloseTimer}
          />
        </div>
      )}
      <LoginIDOptions
        currentLoginID={(value) => {
          setCurrentLoginID(value);
        }}
      />
      {currentLoginID ? (
        <form onSubmit={handleSubmit}>
          {currentLoginID?.prefixes?.length > 0 ? (
            <>
              <InputWithPrefix
                currentLoginID={currentLoginID}
                login="Password"
                countryCode={(val) => {
                  setCountryCode(val);
                }}
                individualId={(val) => {
                  setIndividualId(val);
                }}
                isBtnDisabled={(val) => {
                  setIsBtnDisabled(val);
                }}
                i18nPrefix={i18nKeyPrefix1}
              />

              {fields.map(
                (field, idx) =>
                  idx === 1 && (
                    <div className="-space-y-px" key={idx}>
                      <InputWithImage
                        key={'Password_' + currentLoginID.id}
                        handleChange={handlePasswordChange}
                        blurChange={onBlurChange}
                        labelText={t1(field.labelText)}
                        labelFor={'Password_' + currentLoginID.id}
                        id={'Password_' + currentLoginID.id}
                        name={'Password_' + currentLoginID.id}
                        type={field.type}
                        isRequired={field.isRequired}
                        placeholder={t1(field.placeholder)}
                        customClass={inputCustomClass}
                        errorCode={field.errorCode}
                        maxLength={field.maxLength}
                        regex={field.regex}
                        value={password ?? ''}
                      />
                    </div>
                  )
              )}
            </>
          ) : (
            <>
              {fields.map((field, idx) => (
                <div className="-space-y-px" key={idx}>
                  <InputWithImage
                    key={'Password_' + currentLoginID.id}
                    handleChange={
                      idx === 0 ? handleChange : handlePasswordChange
                    }
                    blurChange={idx === 0 ? handleBlur : onBlurChange}
                    labelText={
                      idx === 0
                        ? currentLoginID.input_label
                        : t1(field.labelText)
                    }
                    labelFor={
                      idx === 0
                        ? currentLoginID.id
                        : 'Password_' + currentLoginID.id
                    }
                    id={
                      idx === 0
                        ? currentLoginID.id
                        : 'Password_' + currentLoginID.id
                    }
                    name={
                      idx === 0 ? 'Password_' + currentLoginID.id : 'password'
                    }
                    type={field.type}
                    isRequired={field.isRequired}
                    placeholder={
                      idx === 0
                        ? currentLoginID.input_placeholder
                        : t1(field.placeholder)
                    }
                    customClass={inputCustomClass}
                    imgPath={null}
                    individualId={individualId}
                    isInvalid={!isValid}
                    value={idx === 0 ? (individualId ?? '') : (password ?? '')}
                    errorCode={idx === 1 ? field.errorCode : ''}
                    maxLength={idx === 1 ? field.maxLength : ''}
                    regex={idx === 1 ? field.regex : ''}
                    currenti18nPrefix={idx === 0 ? i18nKeyPrefix1 : ''}
                    idx={idx}
                  />
                </div>
              ))}
            </>
          )}

          {forgotPassword && (
            <a
              className="forgot-password-hyperlink"
              id="forgot-password-hyperlink"
              href={forgotPasswordURL}
              onClick={() => handleForgotPassword()}
              target="_self"
            >
              {t1('forgot_password')}
            </a>
          )}

          {showCaptcha && (
            <div className="block password-google-reCaptcha">
              <ReCAPTCHA
                hl={i18n.language}
                ref={_reCaptchaRef}
                onChange={handleCaptchaChange}
                sitekey={captchaSiteKey}
                className="flex place-content-center"
              />
            </div>
          )}

          <FormAction
            type={buttonTypes.submit}
            text={t1('login')}
            id="verify_password"
            customClassName="mt-4"
            disabled={
              !individualId ||
              !password?.trim() ||
              isBtnDisabled ||
              (inputErrorBanner && inputErrorBanner.length > 0) ||
              (showCaptcha && captchaToken === null)
            }
          />
        </form>
      ) : (
        <div className="py-6">
          <LoadingIndicator size="medium" message="loading_msg" />
        </div>
      )}
      {status === states.LOADING && (
        <div className="mt-2">
          <LoadingIndicator size="medium" message="authenticating_msg" />
        </div>
      )}
    </>
  );
}
