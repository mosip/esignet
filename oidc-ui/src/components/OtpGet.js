import { useEffect, useRef, useState } from 'react';
import LoadingIndicator from '../common/LoadingIndicator';
import FormAction from './FormAction';
import { LoadingStates as states } from '../constants/states';
import { useTranslation } from 'react-i18next';
import InputWithImage from './InputWithImage';
import { buttonTypes, configurationKeys } from '../constants/clientConstants';
import ReCAPTCHA from 'react-google-recaptcha';
import ErrorBanner from '../common/ErrorBanner';
import langConfigService from '../services/langConfigService';
import redirectOnError from '../helpers/redirectOnError';
import LoginIDOptions from './LoginIDOptions';
import InputWithPrefix from './InputWithPrefix';

export default function OtpGet({
  param,
  authService,
  openIDConnectService,
  onOtpSent,
  i18nKeyPrefix1 = 'otp',
  i18nKeyPrefix2 = 'errors',
  getCaptchaToken,
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
  let fieldsState = {};
  fields.forEach((field) => (fieldsState['Otp_' + field.id] = ''));

  const post_SendOtp = authService.post_SendOtp;

  const commaSeparatedChannels =
    openIDConnectService.getEsignetConfiguration(
      configurationKeys.sendOtpChannels
    ) ?? process.env.REACT_APP_SEND_OTP_CHANNELS;

  const captchaEnableComponents =
    openIDConnectService.getEsignetConfiguration(
      configurationKeys.captchaEnableComponents
    ) ?? process.env.REACT_APP_CAPTCHA_ENABLE;

  const captchaEnableComponentsList = captchaEnableComponents
    .split(',')
    .map((x) => x.trim().toLowerCase());

  const [showCaptcha, setShowCaptcha] = useState(
    captchaEnableComponentsList.indexOf('send-otp') !== -1
  );

  const captchaSiteKey =
    openIDConnectService.getEsignetConfiguration(
      configurationKeys.captchaSiteKey
    ) ?? process.env.REACT_APP_CAPTCHA_SITE_KEY;

  const [status, setStatus] = useState({ state: states.LOADED, msg: '' });
  const [errorBanner, setErrorBanner] = useState(null);

  const [captchaToken, setCaptchaToken] = useState(null);
  const _reCaptchaRef = useRef(null);

  const [currentLoginID, setCurrentLoginID] = useState(null);
  const [countryCode, setCountryCode] = useState(null);
  const [individualId, setIndividualId] = useState(null);
  const [selectedCountry, setSelectedCountry] = useState(null);
  const [isValid, setIsValid] = useState(false);
  const [isBtnDisabled, setIsBtnDisabled] = useState(true);
  const [prevLanguage, setPrevLanguage] = useState(i18n.language);

  useEffect(() => {
    let loadComponent = async () => {
      i18n.on('languageChanged', function () {
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

  const handleCaptchaChange = (value) => {
    setCaptchaToken(value);
    getCaptchaToken(value);
  };

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

  useEffect(() => {
    if (i18n.language === prevLanguage) {
      setIndividualId(null);
      setIsValid(false);
      setIsBtnDisabled(true);
      onCloseHandle();
      if (currentLoginID && currentLoginID.prefixes) {
        setSelectedCountry(currentLoginID.prefixes[0]);
      }
    } else {
      setPrevLanguage(i18n.language);
    }
  }, [currentLoginID]);

  /**
   * Reset the captcha widget
   * & its token value
   */
  const resetCaptcha = () => {
    _reCaptchaRef.current.reset();
    setCaptchaToken(null);
    getCaptchaToken(null);
  };

  const sendOTP = async () => {
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

      let otpChannels = commaSeparatedChannels.split(',').map((x) => x.trim());

      setStatus({ state: states.LOADING, msg: 'sending_otp_msg' });
      const sendOtpResponse = await post_SendOtp(
        transactionId,
        ID,
        otpChannels,
        captchaToken
      );
      setStatus({ state: states.LOADED, msg: '' });

      const { response, errors } = sendOtpResponse;

      if (errors !== null && errors.length > 0) {
        if (errors[0].errorCode === 'invalid_transaction') {
          redirectOnError(errors[0].errorCode, t2(`${errors[0].errorCode}`));
          return;
        }
        const fieldLabel = currentLoginID?.id ? t1(currentLoginID.id) : 'ID';
        const field =
          !fieldLabel ||
          fieldLabel === currentLoginID?.id ||
          fieldLabel.startsWith('otp.')
            ? 'ID'
            : fieldLabel;
        let errorCodeCondition =
          langConfig.errors.otp[errors[0].errorCode] !== undefined &&
          langConfig.errors.otp[errors[0].errorCode] !== null;

        if (errorCodeCondition) {
          setErrorBanner({
            errorCode: `otp.${errors[0].errorCode}`,
            show: true,
            field: field,
          });
        } else {
          setErrorBanner({
            errorCode: `${errors[0].errorCode}`,
            show: true,
            field: field,
          });
        }
        if (showCaptcha) {
          resetCaptcha();
        }
        return;
      } else {
        onOtpSent(
          { prefix: prefix, id: id, postfix: postfix },
          response,
          currentLoginID,
          selectedCountry
        );
        setErrorBanner(null);
      }
    } catch (error) {
      setErrorBanner({
        errorCode: 'otp.send_otp_failed_msg',
        show: true,
      });
      setStatus({ state: states.ERROR, msg: '' });
      if (showCaptcha) {
        resetCaptcha();
      }
    }
  };

  const onCloseHandle = () => {
    setErrorBanner(null);
  };

  return (
    <>
      {errorBanner !== null && (
        <div className="mb-4">
          <ErrorBanner
            showBanner={errorBanner.show}
            errorCode={t2(errorBanner.errorCode, {
              field: errorBanner.field,
            })}
            onCloseHandle={onCloseHandle}
          />
        </div>
      )}
      <LoginIDOptions
        currentLoginID={(value) => {
          setCurrentLoginID(value);
        }}
      />
      {currentLoginID ? (
        <div className="mt-0">
          {currentLoginID?.prefixes?.length > 0 ? (
            <InputWithPrefix
              currentLoginID={currentLoginID}
              login="Otp"
              countryCode={(val) => {
                setCountryCode(val);
              }}
              selectedCountry={(val) => {
                setSelectedCountry(val);
              }}
              individualId={(val) => {
                setIndividualId(val);
              }}
              isBtnDisabled={(val) => {
                setIsBtnDisabled(val);
              }}
              i18nPrefix={i18nKeyPrefix1}
            />
          ) : (
            <>
              {fields.map((field) => (
                <InputWithImage
                  key={'Otp_' + currentLoginID.id}
                  handleChange={handleChange}
                  blurChange={handleBlur}
                  labelText={currentLoginID.input_label}
                  labelFor={'Otp_' + currentLoginID.id}
                  id={'Otp_' + currentLoginID.id}
                  name={'Otp_' + currentLoginID.id}
                  type={field.type}
                  placeholder={currentLoginID.input_placeholder}
                  customClass={inputCustomClass}
                  tooltipMsg="vid_info"
                  errorCode={field.errorCode}
                  individualId={individualId}
                  isInvalid={!isValid}
                  value={individualId ?? ''}
                  currenti18nPrefix={i18nKeyPrefix1}
                />
              ))}
            </>
          )}

          {showCaptcha && (
            <div className="flex justify-center mt-5 mb-2">
              <ReCAPTCHA
                hl={i18n.language}
                ref={_reCaptchaRef}
                onChange={handleCaptchaChange}
                sitekey={captchaSiteKey}
              />
            </div>
          )}

          <div className="mt-5 mb-2">
            <FormAction
              type={buttonTypes.button}
              text={t1('get_otp')}
              handleClick={sendOTP}
              id="get_otp"
              disabled={
                !individualId ||
                isBtnDisabled ||
                (showCaptcha && captchaToken === null)
              }
            />
          </div>

          {status.state === states.LOADING && (
            <LoadingIndicator size="medium" message={status.msg} />
          )}
        </div>
      ) : (
        <div className="py-6">
          <LoadingIndicator size="medium" message="loading_msg" />
        </div>
      )}
    </>
  );
}
