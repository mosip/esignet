import { useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import LoadingIndicator from '../common/LoadingIndicator';
import { challengeFormats, challengeTypes } from '../constants/clientConstants';
import redirectOnError from '../helpers/redirectOnError';
import { useTranslation } from 'react-i18next';
import { getOauthDetailsHash, base64UrlDecode } from '../helpers/utils';
import { Buffer } from 'buffer';

export default function IdToken({
  authService,
  openIDConnectService,
  i18nKeyPrefix1 = 'errors',
}) {
  const { t, i18n } = useTranslation('translation', {
    keyPrefix: i18nKeyPrefix1,
  });
  const navigate = useNavigate();
  const post_AuthenticateUser = authService.post_AuthenticateUser;
  const buildRedirectParams = authService.buildRedirectParamsV2;
  const [searchParams] = useSearchParams();

  console.log('IdToken component mounted');
  console.log(searchParams.toString());

  const extractParam = (param) => searchParams.get(param);

  const getDataFromCookie = (idTokenHint) => {
    const uuid = JSON.parse(base64UrlDecode(idTokenHint.split('.')[1])).sub;

    const code = 'code';

    return { uuid, code };
  };

  //Handle Login API Integration here
  const authenticateUser = async () => {
    try {
      let transactionId = openIDConnectService.getTransactionId();
      let challengeType = challengeTypes.idt;
      let challengeFormat = challengeFormats.idt;
      const idtFromAuthorizeQueryParam = (encoded) =>
        new URLSearchParams(
          decodeURIComponent(Buffer.from(encoded, 'base64').toString('utf-8'))
        ).get('id_token_hint') || null;

      const idtHint =
        extractParam('id_token_hint') ||
        idtFromAuthorizeQueryParam(authService.getAuthorizeQueryParam());

      const { uuid, code } = getDataFromCookie(idtHint);
      const challenge = { token: idtHint, code };
      const encodedChallenge = btoa(JSON.stringify(challenge));
      const challengeList = [
        {
          format: challengeFormat,
          challenge: encodedChallenge,
          authFactorType: challengeType,
        },
      ];

      const oAuthDetails = openIDConnectService.getOAuthDetails();
      const hash = await getOauthDetailsHash(oAuthDetails);
      // Authenticate the user with the provided transactionId, uuid, challengeList, and hash
      // The hash is used to verify the integrity of the OAuth details
      const authenticateResponse = await post_AuthenticateUser(
        transactionId,
        uuid,
        challengeList,
        null,
        hash
      );

      const { response, errors } = authenticateResponse;

      if (errors !== null && errors.length > 0) {
        redirectOnError(errors[0].errorCode, t(errors[0].errorCode));
      } else {
        let params = buildRedirectParams({
          nonce: openIDConnectService.getNonce(),
          state: openIDConnectService.getState(),
          oauthResponse: openIDConnectService.getOAuthDetails(),
          consentAction: response.consentAction,
          ui_locales: extractParam('ui_locales') ?? i18n.language,
        });

        navigate(process.env.PUBLIC_URL + '/claim-details' + params, {
          replace: true,
        });
      }
    } catch (error) {
      redirectOnError('authorization_failed_msg', error.message);
    }
  };

  useEffect(() => {
    authenticateUser();
  }, []);

  return (
    <>
      <div className="my-[6em]">
        <LoadingIndicator size="medium" message="redirecting_msg" />
      </div>
    </>
  );
}
