package io.mosip.idp.binding.services;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTParser;
import io.mosip.idp.binding.TestUtil;
import io.mosip.idp.binding.entity.PublicKeyRegistry;
import io.mosip.idp.binding.repository.PublicKeyRegistryRepository;
import io.mosip.idp.core.dto.AuthChallenge;
import io.mosip.idp.core.dto.ValidateBindingRequest;
import io.mosip.idp.core.dto.ValidateBindingResponse;
import io.mosip.idp.core.exception.IdPException;
import io.mosip.idp.core.util.ErrorConstants;
import io.mosip.idp.core.util.IdentityProviderUtil;
import io.mosip.kernel.keymanagerservice.util.KeymanagerUtil;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.x509.X509V3CertificateGenerator;
import org.jose4j.jws.JsonWebSignature;
import org.json.simple.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import javax.security.auth.x500.X500Principal;
import java.io.IOException;
import java.io.StringWriter;
import java.math.BigInteger;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class KeyBindingValidatorServiceTest {

    @InjectMocks
    KeyBindingValidatorService keyBindingValidatorService;

    @Mock
    private PublicKeyRegistryRepository publicKeyRegistryRepository;

    @Mock
    KeyBindingHelperService keyBindingHelperService;

    @Mock
    KeymanagerUtil keymanagerUtil;

    private JWK clientJWK = TestUtil.generateJWK_RSA();

    private String validateBindingUrl = "http://localhost:8087/v1/idpbinding/validate-binding";

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        ReflectionTestUtils.setField(keyBindingValidatorService, "validateBindingUrl", validateBindingUrl);

        keyBindingHelperService = mock(KeyBindingHelperService.class);
        ReflectionTestUtils.setField(keyBindingHelperService, "saltLength", 10);
        ReflectionTestUtils.setField(keyBindingHelperService, "publicKeyRegistryRepository", publicKeyRegistryRepository);
        ReflectionTestUtils.setField(keyBindingHelperService, "keymanagerUtil", keymanagerUtil);

		when(keyBindingHelperService.getIndividualIdHash(anyString())).thenReturn("id-hash");
        ReflectionTestUtils.setField(keyBindingValidatorService, "keyBindingHelperService", keyBindingHelperService);
		ReflectionTestUtils.setField(keyBindingValidatorService, "keymanagerUtil", keymanagerUtil);
    }

    @Test
	public void validateBinding_withValidDetails_thenPass() throws Exception {
		ValidateBindingRequest validateBindingRequest = new ValidateBindingRequest();
		validateBindingRequest.setTransactionId("909422113");
		validateBindingRequest.setIndividualId("8267411571");
		AuthChallenge authChallenge = new AuthChallenge();
		authChallenge.setAuthFactorType("WLA");
		authChallenge.setFormat("jwt");

		X509Certificate certificate = getCertificate(clientJWK);
		String wlaToken = signJwt(validateBindingRequest.getIndividualId(), certificate, true);
		authChallenge.setChallenge(wlaToken);
		validateBindingRequest.setChallenges(Arrays.asList(authChallenge));

		PublicKeyRegistry publicKeyRegistry = new PublicKeyRegistry("id-hash", "WLA", "test-psu-token", clientJWK.toJSONString(),
				LocalDateTime.now().plusDays(4), "test-binding-id", "test-public-key-hash",
				getPemData(certificate), LocalDateTime.now());
		when(publicKeyRegistryRepository.findByIdHashAndAuthFactorInAndExpiredtimesGreaterThan(anyString(), any(), any()))
				.thenReturn(Arrays.asList(publicKeyRegistry));
		when(keymanagerUtil.convertToCertificate(anyString())).thenReturn(certificate);

		ValidateBindingResponse validateBindingResponse = keyBindingValidatorService.validateBinding(validateBindingRequest);
		Assert.assertEquals(validateBindingResponse.getTransactionId(), validateBindingRequest.getTransactionId());
	}

	@Test
	public void validateBinding_withInvalidSha256Thumbprint_thenFail() throws Exception {
		ValidateBindingRequest validateBindingRequest = new ValidateBindingRequest();
		validateBindingRequest.setTransactionId("909422113");
		validateBindingRequest.setIndividualId("8267411571");
		AuthChallenge authChallenge = new AuthChallenge();
		authChallenge.setAuthFactorType("WLA");
		authChallenge.setFormat("jwt");

		X509Certificate certificate = getCertificate(clientJWK);
		String wlaToken = signJwt(validateBindingRequest.getIndividualId(), certificate, true);
		JWT jwt = JWTParser.parse(wlaToken);
		net.minidev.json.JSONObject headerJson = jwt.getHeader().toJSONObject();
		headerJson.put("x5t#S256", "test-header");
		String[] chunks = wlaToken.split("\\.");
		authChallenge.setChallenge(IdentityProviderUtil.b64Encode(headerJson.toJSONString())+"."+chunks[1]+"."+chunks[2]);
		validateBindingRequest.setChallenges(Arrays.asList(authChallenge));

		PublicKeyRegistry publicKeyRegistry = new PublicKeyRegistry("id-hash", "WLA", "test-psu-token", clientJWK.toJSONString(),
				LocalDateTime.now().plusDays(4), "test-binding-id", "test-public-key-hash",
				getPemData(certificate), LocalDateTime.now());
		when(publicKeyRegistryRepository.findByIdHashAndAuthFactorInAndExpiredtimesGreaterThan(anyString(), any(), any()))
				.thenReturn(Arrays.asList(publicKeyRegistry));
		when(keymanagerUtil.convertToCertificate(anyString())).thenReturn(certificate);

		try {
			keyBindingValidatorService.validateBinding(validateBindingRequest);
			Assert.fail();
		} catch (IdPException e) {
			Assert.assertTrue(e.getErrorCode().equals(ErrorConstants.INVALID_CHALLENGE));
		}
	}

	@Test
	public void validateBinding_withoutSha256Thumbprint_thenFail() throws Exception {
		ValidateBindingRequest validateBindingRequest = new ValidateBindingRequest();
		validateBindingRequest.setTransactionId("909422113");
		validateBindingRequest.setIndividualId("8267411571");
		AuthChallenge authChallenge = new AuthChallenge();
		authChallenge.setAuthFactorType("WLA");
		authChallenge.setFormat("jwt");

		X509Certificate certificate = getCertificate(clientJWK);
		String wlaToken = signJwt(validateBindingRequest.getIndividualId(), certificate, false);
		authChallenge.setChallenge(wlaToken);
		validateBindingRequest.setChallenges(Arrays.asList(authChallenge));

		PublicKeyRegistry publicKeyRegistry = new PublicKeyRegistry("id-hash", "WLA", "test-psu-token", clientJWK.toJSONString(),
				LocalDateTime.now().plusDays(4), "test-binding-id", "test-public-key-hash",
				getPemData(certificate), LocalDateTime.now());
		when(publicKeyRegistryRepository.findByIdHashAndAuthFactorInAndExpiredtimesGreaterThan(anyString(), any(), any()))
				.thenReturn(Arrays.asList(publicKeyRegistry));
		when(keymanagerUtil.convertToCertificate(anyString())).thenReturn(certificate);

		try {
			keyBindingValidatorService.validateBinding(validateBindingRequest);
			Assert.fail();
		} catch (IdPException e) {
			Assert.assertTrue(e.getErrorCode().equals(ErrorConstants.INVALID_CHALLENGE));
		}
	}

	@Test
	public void validateBinding_withUnBoundId_thenFail() throws IdPException {
		when(publicKeyRegistryRepository.findByIdHashAndAuthFactorInAndExpiredtimesGreaterThan(anyString(), any(), any())).thenReturn(Arrays.asList());

		ValidateBindingRequest validateBindingRequest = new ValidateBindingRequest();
		validateBindingRequest.setTransactionId("909422113");
		validateBindingRequest.setIndividualId("8267411571");
		AuthChallenge authChallenge = new AuthChallenge();
		authChallenge.setAuthFactorType("WLA");
		authChallenge.setFormat("jwt");
		authChallenge.setChallenge("wlaToken");
		validateBindingRequest.setChallenges(Arrays.asList(authChallenge));

		try {
			keyBindingValidatorService.validateBinding(validateBindingRequest);
			Assert.fail();
		} catch (IdPException e) {
			Assert.assertTrue(e.getErrorCode().equals(ErrorConstants.KEY_BINDING_NOT_FOUND));
		}
	}

	@Test
	public void validateBinding_withUnBoundAuthFactors_thenFail() throws IdPException {
		PublicKeyRegistry publicKeyRegistry = new PublicKeyRegistry("id-hash", "WLA", "test-psu-token", clientJWK.toJSONString(),
				LocalDateTime.now().plusDays(4), "test-binding-id", "test-public-key-hash",
				"certificate", LocalDateTime.now());
		when(publicKeyRegistryRepository.findByIdHashAndAuthFactorInAndExpiredtimesGreaterThan(anyString(), any(), any())).thenReturn(Arrays.asList(publicKeyRegistry));

		ValidateBindingRequest validateBindingRequest = new ValidateBindingRequest();
		validateBindingRequest.setTransactionId("909422113");
		validateBindingRequest.setIndividualId("8267411571");
		AuthChallenge authChallengeWLA = new AuthChallenge();
        authChallengeWLA.setAuthFactorType("WLA");
        authChallengeWLA.setFormat("jwt");
        authChallengeWLA.setChallenge("wlaToken");
        AuthChallenge authChallengeHWA = new AuthChallenge();
        authChallengeHWA.setAuthFactorType("HLA");
        authChallengeHWA.setFormat("jwt");
        authChallengeHWA.setChallenge("hlaToken");
		validateBindingRequest.setChallenges(Arrays.asList(authChallengeWLA, authChallengeHWA));

		try {
			keyBindingValidatorService.validateBinding(validateBindingRequest);
			Assert.fail();
		} catch (IdPException e) {
			Assert.assertTrue(e.getErrorCode().equals(ErrorConstants.UNBOUND_AUTH_FACTOR));
		}
	}

	@Test
	public void validateBinding_withInvalidChallenge_thenFail() throws IdPException {
        PublicKeyRegistry publicKeyRegistry = new PublicKeyRegistry("id-hash", "WLA", "test-psu-token", clientJWK.toJSONString(),
                LocalDateTime.now().plusDays(4), "test-binding-id", "test-public-key-hash",
                "certificate", LocalDateTime.now());
        when(publicKeyRegistryRepository.findByIdHashAndAuthFactorInAndExpiredtimesGreaterThan(anyString(), any(), any())).thenReturn(Arrays.asList(publicKeyRegistry));

		ValidateBindingRequest validateBindingRequest = new ValidateBindingRequest();
		validateBindingRequest.setTransactionId("909422113");
		validateBindingRequest.setIndividualId("8267411571");
		AuthChallenge authChallenge = new AuthChallenge();
		authChallenge.setAuthFactorType("WLA");
		authChallenge.setFormat("jwt");
		authChallenge.setChallenge("wlaToken");
		validateBindingRequest.setChallenges(Arrays.asList(authChallenge));

		try {
			keyBindingValidatorService.validateBinding(validateBindingRequest);
			Assert.fail();
		} catch (IdPException e) {
			Assert.assertTrue(e.getErrorCode().equals(ErrorConstants.INVALID_CHALLENGE));
		}
	}

	private X509Certificate getCertificate(JWK jwk) throws Exception {
		X509V3CertificateGenerator generator = new X509V3CertificateGenerator();
		X500Principal dnName = new X500Principal("CN=Test");
		generator.setSubjectDN(dnName);
		generator.setIssuerDN(dnName); // use the same
		generator.setNotBefore(new Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000));
		generator.setNotAfter(new Date(System.currentTimeMillis() + 2 * 365 * 24 * 60 * 60 * 1000));
		generator.setPublicKey(jwk.toRSAKey().toPublicKey());
		generator.setSignatureAlgorithm("SHA256WITHRSA");
		generator.setSerialNumber(new BigInteger(String.valueOf(System.currentTimeMillis())));
		return generator.generate(jwk.toRSAKey().toPrivateKey());
	}

	private String signJwt(String individualId, X509Certificate certificate, boolean addSha256Thumbprint) throws Exception {
		JSONObject payload = new JSONObject();
		payload.put("iss", "test-app");
		payload.put("aud", validateBindingUrl);
		payload.put("sub", individualId);
		payload.put("iat", IdentityProviderUtil.getEpochSeconds());
		payload.put("exp", IdentityProviderUtil.getEpochSeconds()+3600);

		JsonWebSignature jwSign = new JsonWebSignature();
		jwSign.setKeyIdHeaderValue(certificate.getSerialNumber().toString(10));
		if(addSha256Thumbprint) {
			jwSign.setX509CertSha256ThumbprintHeaderValue(certificate);
		}
		jwSign.setPayload(payload.toJSONString());
		jwSign.setAlgorithmHeaderValue("RS256");
		jwSign.setKey(clientJWK.toRSAKey().toPrivateKey());
		jwSign.setDoKeyValidation(false);
		return jwSign.getCompactSerialization();
	}

	private String getPemData(Object anyObject) throws IOException {
		StringWriter stringWriter = new StringWriter();
		try (JcaPEMWriter pemWriter = new JcaPEMWriter(stringWriter)) {
			pemWriter.writeObject(anyObject);
			pemWriter.flush();
			return stringWriter.toString();
		}
	}
}
