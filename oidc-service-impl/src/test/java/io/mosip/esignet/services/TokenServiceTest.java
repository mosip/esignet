package io.mosip.esignet.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.esignet.core.dto.OIDCTransaction;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.core.exception.InvalidRequestException;
import io.mosip.esignet.core.exception.NotAuthenticatedException;
import io.mosip.esignet.core.util.AuthenticationContextClassRefUtil;
import io.mosip.esignet.core.util.IdentityProviderUtil;
import io.mosip.kernel.core.signatureutil.model.SignatureResponse;
import io.mosip.kernel.signature.dto.*;
import io.mosip.kernel.signature.service.SignatureService;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.HashSet;

import static io.mosip.esignet.core.spi.TokenService.*;

@RunWith(MockitoJUnitRunner.class)
public class TokenServiceTest {

    @InjectMocks
    private TokenServiceImpl tokenService;

    @Mock
    private AuthenticationContextClassRefUtil authenticationContextClassRefUtil;

    private String testKey = "{\n" +
            "    \"p\": \"2dpXAH1LB25KbcYxFfOktFi0-XTmyvOB1BoByJs-JjVDrgTKVLDbXiqW8xf-GcBBB5TyN7dN6dX66RJvF0-6jsXpq3t7keCnUAe4-yLCCOeivYVVVRw9phx7tC5gflguRBts3GDy3h4RvQViQU6iMdXEAU7h5rut_-zR-fxFZpk\",\n" +
            "    \"kty\": \"RSA\",\n" +
            "    \"q\": \"oIXJf5p24VkS_L9wNd1Op5TLV-TS3koL5rd7rNhyg7SymZnE1Xym8yGqwfLSRTCZVxSu0NIIo5OLCNxLPBQ9Qm5DAY4Tim-Je1bNrEXgwiX-7I8_7oWypfuNLc_fIuUaMbkIzKov33uD5xkT_Kxp_SLUOnL5KrPfBbFJYGwb_Y8\",\n" +
            "    \"d\": \"e2tQ1fYvISJjCdp76RjC5XbR6U2hOajC79J7Y6SqLhkCEqjwAk_VhyCz5IembiJwUZ2y-8B9OxEUdspXcBSWY2kgzXtWNT-wxIRZfiRUDCWvwdNDe95ozoOziDOVN-y0i_brb7YOjRwsOyaaRVs5VfjKb3cb3eJLY-JRM5pMAKdxv6hKxczZ8epLGS9djRbkMqdetXuAhI5t7amtZ2D9szUHlCd998kh-10f8yULOyBccGOswauGMfhQzitFj0LRR3VTho6UsVQRaVhEjbuMKnf981je-gL8iGZe80lf8uaKXhb8vja3iTBqmfT4SOFaM3SeAt0UZkZaBKsssDTHwQ\",\n" +
            "    \"e\": \"AQAB\",\n" +
            "    \"use\": \"sig\",\n" +
            "    \"qi\": \"Z5UeoXdXK4FTVIiQiJecGWWgIbyGGM5EFB4wQdTpspk8bBFwI3-k24_xCRonCyMjATZzcYhTlUteraaei6WmisKRL-ugsS2g-sYzQFXQ5o4zEp74rB3kIGCbIK1a0x05jRFk-PCEBScypHNzk4IhuBLFId_SiQFICYq6GGVGj-0\",\n" +
            "    \"dp\": \"auubSbU0rsf1pZzhGHoE-zKSV-CFKVSMArJk77Upsozv06esOha3A6d5gIPlBXRzNipnGutPRRXtWJjghxttX4dJIQ2w3y7YTxILOs6bVs2A9O1MrUH4C9_s4sjkOP5Ebs7bBepbKKKvaAsNZyoVtsnIsi-p9ZllU7dCcyPaV_k\",\n" +
            "    \"alg\": \"RS256\",\n" +
            "    \"dq\": \"L5X5md5Mh5lES7DkrtMgUgWGElQ_Pq5swMR74U15BRo4J9ixxSfixgig-kXll6VEj9AN0tGwxe0jNkk39GN7lYniSz-3Az71Xp7o8bz1WBizbaU5qpfv0cy0mXQaDdok3cCgnyuEbZfMDmIczra95NDCYWFcBBC2eJWJzw-9bHk\",\n" +
            "    \"n\": \"iJpQSIajCvz1AI9bGhT6MuuboJr_dfgz_NdkCVbA6CpntZ14tRmTqs2aBhpMovIkF6Y7Az-7W-jBTze68GavFRQ8Epdn4ucbDGMekaOOjgYsaIlno1A_AVnieqTMdl31jrTAiwxtPcSVlp-23UfQwi8TUXpMfqbbI5kW3uXDfAjSLBTa16XStOD93ONNFKPzmdlr2SfL7ppZAUnVMeXHEnVms5EygqANoSF39jQ8SOlGb-_8BYapw2AVaa_hDg3aEWzduAckwJGmyByiR_fndVfSWtNKLp1m3K17dyaepYGWT3V7esPJuPSMa2IAMqvnrBlfXOhu2qDtqVXu30yEdw\"\n" +
            "}";

    private String publidKey = "{\n" +
            "    \"kty\": \"RSA\",\n" +
            "    \"e\": \"AQAB\",\n" +
            "    \"use\": \"sig\",\n" +
            "    \"alg\": \"RS256\",\n" +
            "    \"n\": \"iJpQSIajCvz1AI9bGhT6MuuboJr_dfgz_NdkCVbA6CpntZ14tRmTqs2aBhpMovIkF6Y7Az-7W-jBTze68GavFRQ8Epdn4ucbDGMekaOOjgYsaIlno1A_AVnieqTMdl31jrTAiwxtPcSVlp-23UfQwi8TUXpMfqbbI5kW3uXDfAjSLBTa16XStOD93ONNFKPzmdlr2SfL7ppZAUnVMeXHEnVms5EygqANoSF39jQ8SOlGb-_8BYapw2AVaa_hDg3aEWzduAckwJGmyByiR_fndVfSWtNKLp1m3K17dyaepYGWT3V7esPJuPSMa2IAMqvnrBlfXOhu2qDtqVXu30yEdw\"\n" +
            "}";


    @Before
    public void setup() {
        ReflectionTestUtils.setField(tokenService, "signatureService", getSignatureService());
        ReflectionTestUtils.setField(tokenService, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(tokenService, "issuerId", "test-issuer");
    }

    @Test
    public void getIDToken_test() throws JSONException {
        OIDCTransaction transaction = new OIDCTransaction();
        transaction.setClientId("client-id");
        transaction.setPartnerSpecificUserToken("psut");
        transaction.setNonce("nonce");
        transaction.setAuthTimeInSeconds(22);
        transaction.setAHash("access-token-hash");
        transaction.setProvidedAuthFactors(new HashSet<>());
        Mockito.when(authenticationContextClassRefUtil.getACRs(Mockito.any())).thenReturn(Arrays.asList("generated-code", "static-code"));
        String token = tokenService.getIDToken(transaction);
        Assert.assertNotNull(token);
        JSONObject jsonObject = new JSONObject(new String(IdentityProviderUtil.b64Decode(token)));
        Assert.assertEquals(transaction.getClientId(), jsonObject.get(AUD));
        Assert.assertEquals(transaction.getPartnerSpecificUserToken(), jsonObject.get(SUB));
        Assert.assertEquals(transaction.getAuthTimeInSeconds(), jsonObject.getLong(AUTH_TIME));
        Assert.assertEquals(transaction.getNonce(), jsonObject.get(NONCE));
        Assert.assertEquals("generated-code static-code", jsonObject.get(ACR));
        Assert.assertEquals("test-issuer", jsonObject.get(ISS));
    }

    @Test
    public void getAccessToken_test() throws JSONException {
        OIDCTransaction transaction = new OIDCTransaction();
        transaction.setClientId("client-id");
        transaction.setPartnerSpecificUserToken("psut");
        transaction.setPermittedScopes(Arrays.asList("read", "write"));
        String token = tokenService.getAccessToken(transaction, null);
        Assert.assertNotNull(token);
        JSONObject jsonObject = new JSONObject(new String(IdentityProviderUtil.b64Decode(token)));
        Assert.assertEquals(transaction.getClientId(), jsonObject.get(AUD));
        Assert.assertEquals(transaction.getPartnerSpecificUserToken(), jsonObject.get(SUB));
        Assert.assertEquals("read write", jsonObject.get(SCOPE));
        Assert.assertEquals("test-issuer", jsonObject.get(ISS));
    }

    @Test
    public void getAccessTokenWithNonce_test() throws JSONException {
        OIDCTransaction transaction = new OIDCTransaction();
        transaction.setClientId("client-id");
        transaction.setPartnerSpecificUserToken("psut");
        transaction.setPermittedScopes(Arrays.asList("read", "write"));
        String token = tokenService.getAccessToken(transaction, "test_cnonce");
        Assert.assertNotNull(token);
        JSONObject jsonObject = new JSONObject(new String(IdentityProviderUtil.b64Decode(token)));
        Assert.assertEquals(transaction.getClientId(), jsonObject.get(AUD));
        Assert.assertEquals(transaction.getPartnerSpecificUserToken(), jsonObject.get(SUB));
        Assert.assertEquals("read write", jsonObject.get(SCOPE));
        Assert.assertEquals("test-issuer", jsonObject.get(ISS));
        Assert.assertEquals("test_cnonce", jsonObject.get(C_NONCE));
        Assert.assertNotNull(jsonObject.get(C_NONCE_EXPIRES_IN));
    }

    @Test(expected = EsignetException.class)
    public void verifyClientAssertionToken_withNullAssertion_thenFail() {
        tokenService.verifyClientAssertionToken("client-id", publidKey, null,"audience");
    }

    @Test(expected = InvalidRequestException.class)
    public void verifyClientAssertionToken_withInvalidToken_thenFail() {
        tokenService.verifyClientAssertionToken("client-id", publidKey, "client-assertion","audience");
    }

    @Test(expected = NotAuthenticatedException.class)
    public void verifyAccessToken_withNullToken_thenFail() {
        tokenService.verifyAccessToken("client-id", publidKey, null);
    }

    @Test(expected = NotAuthenticatedException.class)
    public void verifyAccessToken_withInvalidToken_thenFail() {
        tokenService.verifyAccessToken("client-id", publidKey, "access_token");
    }
    
    @Test(expected = NotAuthenticatedException.class)
    public void verifyAccessToken_withInvalidDataToken_thenFail() {
        tokenService.verifyAccessToken("client-id", publidKey, "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkJhZWxkdW5nIFVzZXIiLCJpYXQiOjE1MTYyMzkwMjJ9.qH7Zj_m3kY69kxhaQXTa-ivIpytKXXjZc1ZSmapZnGE");
    }

    @Test(expected = NotAuthenticatedException.class)
    public void verifyIdTokenHint_withNullToken_thenFail() {
        tokenService.verifyIdToken(null,"client-id");
    }

    @Test(expected = NotAuthenticatedException.class)
    public void verifyTokenHint_withInvalidToken_thenFail() {
        tokenService.verifyIdToken("id_token_hint","client-id");
    }

    private SignatureService getSignatureService() {
        return new SignatureService() {

            @Override
            public ValidatorResponseDto validate(TimestampRequestDto timestampRequestDto) {
                return null;
            }

            @Override
            public SignatureResponse sign(SignRequestDto signRequestDto) {
                return null;
            }

            @Override
            public SignatureResponseDto signPDF(PDFSignatureRequestDto request) {
                return null;
            }

            @Override
            public JWTSignatureResponseDto jwtSign(JWTSignatureRequestDto jwtSignRequestDto) {
                JWTSignatureResponseDto responseDto = new JWTSignatureResponseDto();
                responseDto.setJwtSignedData(jwtSignRequestDto.getDataToSign());
                return responseDto;
            }

            @Override
            public JWTSignatureVerifyResponseDto jwtVerify(JWTSignatureVerifyRequestDto jwtSignatureVerifyRequestDto) {
                JWTSignatureVerifyResponseDto jwtSignatureVerifyResponseDto = new JWTSignatureVerifyResponseDto();
                if(jwtSignatureVerifyRequestDto.getJwtSignatureData()==null) {
                    jwtSignatureVerifyResponseDto.setSignatureValid(false);
                    return jwtSignatureVerifyResponseDto;
                }
                jwtSignatureVerifyResponseDto.setSignatureValid(true);
                return jwtSignatureVerifyResponseDto;
            }

            @Override
            public JWTSignatureResponseDto jwsSign(JWSSignatureRequestDto jwsSignRequestDto) {
                return null;
            }
        };
    }
}
