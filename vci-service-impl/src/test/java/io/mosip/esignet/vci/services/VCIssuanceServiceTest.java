package io.mosip.esignet.vci.services;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import foundation.identity.jsonld.JsonLDObject;
import io.mosip.esignet.api.dto.VCResult;
import io.mosip.esignet.api.spi.VCIssuancePlugin;
import io.mosip.esignet.core.dto.vci.*;
import io.mosip.esignet.core.exception.NotAuthenticatedException;
import io.mosip.esignet.core.util.SecurityHelperService;
import io.mosip.esignet.vci.pop.JwtProofValidator;
import io.mosip.esignet.vci.pop.ProofValidatorFactory;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

@RunWith(MockitoJUnitRunner.class)
public class VCIssuanceServiceTest {

    @InjectMocks
    private VCIssuanceServiceImpl vcIssuanceService;

    @Mock
    private VCIssuancePlugin vcIssuancePlugin;

    @Mock
    private ProofValidatorFactory proofValidatorFactory;

    @Mock
    private VCICacheService vciCacheService;

    @Mock
    private ParsedAccessToken parsedAccessToken;

    @Mock
    private JwtProofValidator jwtProofValidator;

    @Mock
    private SecurityHelperService securityHelperService;


    @Before
    public void setup() {
        Map<String, Object> issuerMetadata = new HashMap<>();
        issuerMetadata.put("credential_issuer", "https://localhost:9090");
        issuerMetadata.put("credential_endpoint", "https://localhost:9090/v1/esignet/vci/credential");
        Map<String, Object> supportedCredential = new LinkedHashMap<>();
        supportedCredential.put("format", "ldp_vc");
        supportedCredential.put("id", "SampleVerifiableCredential_ldp");
        supportedCredential.put("scope", "sample_vc_ldp");
        supportedCredential.put("proof_types_supported", Arrays.asList("jwt"));
        supportedCredential.put("credential_definition", null);
        issuerMetadata.put("credentials_supported", Arrays.asList(supportedCredential));
        ReflectionTestUtils.setField(vcIssuanceService, "issuerMetadata", issuerMetadata);
    }


    @Test
    public void getCredential_withValidDetails_thenPass() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("scope", "sample_vc_ldp");
        claims.put("iat", LocalDateTime.now(ZoneOffset.UTC).minusSeconds(10).toEpochSecond(ZoneOffset.UTC));
        claims.put("c_nonce", "test-nonce");
        claims.put("c_nonce_expires_in", 60);
        claims.put("accessTokenHash", "access-token-hash");
        claims.put("client_id", "test-client-id");
        CredentialRequest credentialRequest = new CredentialRequest();
        credentialRequest.setFormat("ldp_vc");
        CredentialDefinition credentialDefinition = new CredentialDefinition();
        credentialDefinition.setType(Arrays.asList("VerifiableCredential", "SampleVerifiableCredential_ldp"));
        credentialDefinition.setContext(Arrays.asList(""));
        credentialRequest.setCredential_definition(credentialDefinition);
        CredentialProof credentialProof = new CredentialProof();
        credentialProof.setProof_type("jwt");
        credentialProof.setJwt("header.payload.signature");
        credentialRequest.setProof(credentialProof);

        VCResult<JsonLDObject> vcResult = new VCResult<>();
        vcResult.setCredential(new JsonLDObject());
        vcResult.setFormat("ldp_vc");

        VCIssuanceTransaction issuanceTransaction = new VCIssuanceTransaction();
        issuanceTransaction.setCNonce("new_c_nonce");
        issuanceTransaction.setCNonceExpireSeconds(400);

        Mockito.when(parsedAccessToken.isActive()).thenReturn(true);
        Mockito.when(parsedAccessToken.getClaims()).thenReturn(claims);
        Mockito.when(proofValidatorFactory.getProofValidator("jwt")).thenReturn(jwtProofValidator);
        Mockito.when(jwtProofValidator.validate(Mockito.anyString(), Mockito.anyString(), Mockito.any(CredentialProof.class)))
                .thenReturn(true);
        Mockito.when(jwtProofValidator.getKeyMaterial(credentialProof)).thenReturn("holder-identifier");
        Mockito.when(vcIssuancePlugin.getVerifiableCredentialWithLinkedDataProof(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(vcResult);
        vcIssuanceService.getCredential(credentialRequest);
    }

    @Test(expected = NotAuthenticatedException.class)
    public void getCredential_withInactiveAccessToken_thenFail() {
        Mockito.when(parsedAccessToken.isActive()).thenReturn(false);
        vcIssuanceService.getCredential(new CredentialRequest());
    }

    private JWK generateJWK_RSA() {
        // Generate the RSA key pair
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            KeyPair keyPair = gen.generateKeyPair();
            // Convert public key to JWK format
            return new RSAKey.Builder((RSAPublicKey)keyPair.getPublic())
                    .privateKey((RSAPrivateKey)keyPair.getPrivate())
                    .keyUse(KeyUse.SIGNATURE)
                    .keyID(UUID.randomUUID().toString())
                    .build();
        } catch (NoSuchAlgorithmException e) {}
        return null;
    }
}
