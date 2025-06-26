import { useEffect } from "react";
import { useNavigate } from "react-router-dom";
import LoadingIndicator from "../common/LoadingIndicator";
import { challengeFormats, challengeTypes } from "../constants/clientConstants";
import redirectOnError from "../helpers/redirectOnError";
import { useTranslation } from "react-i18next";
import { decodeHash } from "../helpers/utils";

export default function IdToken({
  authService,
  openIDConnectService,
  i18nKeyPrefix1 = "errors",
}) {
  const { t } = useTranslation("translation", {
    keyPrefix: i18nKeyPrefix1,
  });
  const navigate = useNavigate();
  const post_AuthenticateUser = authService.post_AuthenticateUser;
  const buildRedirectParams = authService.buildRedirectParams;

  const base64UrlDecode = (str) => {
    return decodeURIComponent(
      decodeHash(str.replace(/-/g, "+").replace(/_/g, "/"))
        .split("")
        .map((c) => `%${("00" + c.charCodeAt(0).toString(16)).slice(-2)}`)
        .join("")
    );
  };

  const getDataFromCookie = (idTokenHint) => {
    const uuid = JSON.parse(base64UrlDecode(idTokenHint.split(".")[1])).sub;

    const code = "code";

    return { uuid, code };
  };

  //Handle Login API Integration here
  const authenticateUser = async () => {
    try {
      let transactionId = openIDConnectService.getTransactionId();
      let challengeType = challengeTypes.idt;
      let challengeFormat = challengeFormats.idt;
      let idtHint = sessionStorage.getItem("idtHint");

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

      const authenticateResponse = await post_AuthenticateUser(
        transactionId,
        uuid,
        challengeList
      );

      const { response, errors } = authenticateResponse;

      if (errors != null && errors.length > 0) {
        redirectOnError(errors[0].errorCode, t(errors[0].errorCode));
      } else {
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
      redirectOnError("authorization_failed_msg", error.message);
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
