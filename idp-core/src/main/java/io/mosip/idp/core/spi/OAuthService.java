package io.mosip.idp.core.spi;

import io.mosip.idp.core.dto.TokenRequest;
import io.mosip.idp.core.dto.TokenResponse;
import io.mosip.idp.core.exception.IdPException;

public interface OAuthService {

    TokenResponse getTokens(TokenRequest tokenRequest) throws IdPException;
}
