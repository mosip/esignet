package io.mosip.esignet.controllers;

import io.mosip.esignet.core.constants.Constants;
import io.mosip.esignet.core.dto.*;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.core.exception.InvalidTransactionException;
import io.mosip.esignet.core.spi.TokenService;
import io.mosip.esignet.core.util.IdentityProviderUtil;
import io.mosip.esignet.services.AuthorizationHelperService;
import io.mosip.esignet.services.CacheUtilService;
import io.mosip.kernel.signature.dto.JWTSignatureRequestDto;
import io.mosip.kernel.signature.dto.JWTSignatureResponseDto;
import io.mosip.kernel.signature.service.SignatureService;
import lombok.extern.slf4j.Slf4j;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static io.mosip.esignet.core.util.IdentityProviderUtil.ALGO_SHA3_256;

@Slf4j
@RestController
@RequestMapping("/proxyauthorization")
public class ProxyAuthorizationController {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private CacheUtilService cacheUtilService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private AuthorizationHelperService authorizationHelperService;

    @Autowired
    private SignatureService signatureService;

    @Value("${mosip.esignet.proxy.relying-party-service-url}")
    private String proxyRelyingPartyServiceUrl;

    @Value("${mosip.esignet.proxy.oidc-client-id}")
    private String proxyOidcClientId;

    @Value("${mosip.esignet.proxy.oidc-client-redirect-uri}")
    private String proxyOidcClientRedirectUri;

    @PostMapping("/auth-code")
    public ResponseWrapper<AuthCodeResponse> getAuthorizationCode(@Valid @RequestBody RequestWrapper<ProxyAuthCodeRequest>
                                                                              requestWrapper) {
        ProxyAuthCodeRequest proxyAuthCodeRequest = requestWrapper.getRequest();
        OIDCTransaction transaction = cacheUtilService.getPreAuthTransaction(proxyAuthCodeRequest.getTransactionId());
        if(transaction == null)
            throw new InvalidTransactionException();

        Map<String, String> proxyKycResponse = fetchUserInfoWithProxyAuthorizationCode(proxyAuthCodeRequest.getProxyAuthorizationCode(),
                proxyOidcClientId, proxyOidcClientRedirectUri);

        String authCode = IdentityProviderUtil.generateB64EncodedHash(ALGO_SHA3_256, UUID.randomUUID().toString());
        String partnerSpecificUserToken = IdentityProviderUtil.generateB64EncodedHash(ALGO_SHA3_256,
                String.join(":", proxyKycResponse.get("sub"), transaction.getRelyingPartyId()));

        transaction.setPartnerSpecificUserToken(partnerSpecificUserToken);
        transaction.setKycToken(proxyKycResponse.get("sub"));
        transaction.setAuthTimeInSeconds(IdentityProviderUtil.getEpochSeconds());
        transaction.setCodeHash(authorizationHelperService.getKeyHash(authCode));
        transaction.setIndividualId(proxyKycResponse.get("sub"));
        transaction.setProxy(true);

        proxyKycResponse.put(TokenService.SUB, partnerSpecificUserToken);
        transaction.setEncryptedKyc(getSignedKyc(proxyKycResponse));
        transaction = cacheUtilService.setProxyAuthCodeGeneratedTransaction(proxyAuthCodeRequest.getTransactionId(), transaction);

        ResponseWrapper<AuthCodeResponse> responseWrapper = getAuthCodeResponseResponseWrapper(authCode, transaction);
        return responseWrapper;
    }

    private String getSignedKyc(Map<String, String> userInfo) {
        JSONObject payload = new JSONObject(userInfo);
        JWTSignatureRequestDto jwtSignatureRequestDto = new JWTSignatureRequestDto();
        jwtSignatureRequestDto.setApplicationId(Constants.OIDC_SERVICE_APP_ID);
        jwtSignatureRequestDto.setReferenceId("");
        jwtSignatureRequestDto.setIncludePayload(true);
        jwtSignatureRequestDto.setIncludeCertificate(false);
        jwtSignatureRequestDto.setDataToSign(IdentityProviderUtil.b64Encode(payload.toJSONString()));
        jwtSignatureRequestDto.setIncludeCertHash(false);
        JWTSignatureResponseDto responseDto = signatureService.jwtSign(jwtSignatureRequestDto);
        return responseDto.getJwtSignedData();
    }

    private ResponseWrapper<AuthCodeResponse> getAuthCodeResponseResponseWrapper(String authCode, OIDCTransaction transaction) {
        ResponseWrapper<AuthCodeResponse> responseWrapper = new ResponseWrapper<>();
        AuthCodeResponse authCodeResponse = new AuthCodeResponse();
        authCodeResponse.setCode(authCode);
        authCodeResponse.setRedirectUri(transaction.getRedirectUri());
        authCodeResponse.setNonce(transaction.getNonce());
        authCodeResponse.setState(transaction.getState());
        responseWrapper.setResponse(authCodeResponse);
        responseWrapper.setResponseTime(IdentityProviderUtil.getUTCDateTime());
        return responseWrapper;
    }

    private Map<String, String> fetchUserInfoWithProxyAuthorizationCode(String proxyCode, String proxyClientId,
                                                                        String proxyRedirectUri) {
        Map<String, String> proxyRequest = new HashMap<>();
        proxyRequest.put("client_id", proxyClientId);
        proxyRequest.put("code", proxyCode);
        proxyRequest.put("grant_type", "authorization_code");
        proxyRequest.put("redirect_uri", proxyRedirectUri);
        ResponseEntity<Map> proxyResponse = restTemplate.postForEntity(proxyRelyingPartyServiceUrl, proxyRequest, Map.class);
        if(proxyResponse.getStatusCode().is2xxSuccessful()) {
            return proxyResponse.getBody();
        }
        throw new EsignetException("proxy_kyc_failed");
    }
}
