package io.mosip.esignet;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTParser;
import io.mosip.esignet.api.dto.BindingAuthResult;
import io.mosip.esignet.api.exception.KycAuthException;
import io.mosip.esignet.entity.PublicKeyRegistry;
import io.mosip.esignet.repository.PublicKeyRegistryRepository;
import io.mosip.esignet.api.dto.AuthChallenge;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.util.IdentityProviderUtil;
import io.mosip.esignet.services.BindingValidatorServiceImpl;
import io.mosip.esignet.services.KeyBindingHelperService;
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

import static io.mosip.esignet.KeyBindingServiceTest.generateJWK_RSA;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class BindingValidatorServiceImplTest {

    @InjectMocks
	BindingValidatorServiceImpl bindingValidatorServiceImpl;

    @Mock
    private PublicKeyRegistryRepository publicKeyRegistryRepository;

    @Mock
	KeyBindingHelperService keyBindingHelperService;

    @Mock
    KeymanagerUtil keymanagerUtil;

    private JWK clientJWK = generateJWK_RSA();

    private String audienceId = "esignet-binding";

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        ReflectionTestUtils.setField(bindingValidatorServiceImpl, "bindingAudienceId", audienceId);

        keyBindingHelperService = mock(KeyBindingHelperService.class);
        ReflectionTestUtils.setField(keyBindingHelperService, "saltLength", 10);
        ReflectionTestUtils.setField(keyBindingHelperService, "publicKeyRegistryRepository", publicKeyRegistryRepository);
        ReflectionTestUtils.setField(keyBindingHelperService, "keymanagerUtil", keymanagerUtil);

		when(keyBindingHelperService.getIndividualIdHash(anyString())).thenReturn("id-hash");
        ReflectionTestUtils.setField(bindingValidatorServiceImpl, "keyBindingHelperService", keyBindingHelperService);
		ReflectionTestUtils.setField(bindingValidatorServiceImpl, "keymanagerUtil", keymanagerUtil);
    }

    @Test
	public void validateBinding_withValidDetails_thenPass() throws Exception {
		String transactionId = "909422113";
		String individualId  = "8267411571";
		AuthChallenge authChallenge = new AuthChallenge();
		authChallenge.setAuthFactorType("WLA");
		authChallenge.setFormat("jwt");

		X509Certificate certificate = getCertificate(clientJWK);
		String wlaToken = signJwt(individualId, certificate, true);
		authChallenge.setChallenge(wlaToken);

		PublicKeyRegistry publicKeyRegistry = new PublicKeyRegistry("id-hash", "WLA", "test-psu-token", clientJWK.toJSONString(),
				LocalDateTime.now().plusDays(4), "test-binding-id", "test-public-key-hash","thumbprint",
				getPemData(certificate), LocalDateTime.now());
		when(publicKeyRegistryRepository.findByIdHashAndAuthFactorInAndExpiredtimesGreaterThan(anyString(), any(), any()))
				.thenReturn(Arrays.asList(publicKeyRegistry));
		when(keymanagerUtil.convertToCertificate(anyString())).thenReturn(certificate);

		BindingAuthResult bindingAuthResult = bindingValidatorServiceImpl.validateBindingAuth(transactionId, individualId, Arrays.asList(authChallenge));
		Assert.assertEquals(bindingAuthResult.getTransactionId(), transactionId);
	}

	@Test
	public void validateBinding_withInvalidSha256Thumbprint_thenFail() throws Exception {
		String transactionId = "909422113";
		String individualId  = "8267411571";
		AuthChallenge authChallenge = new AuthChallenge();
		authChallenge.setAuthFactorType("WLA");
		authChallenge.setFormat("jwt");

		X509Certificate certificate = getCertificate(clientJWK);
		String wlaToken = signJwt(individualId, certificate, true);
		JWT jwt = JWTParser.parse(wlaToken);
		net.minidev.json.JSONObject headerJson = jwt.getHeader().toJSONObject();
		headerJson.put("x5t#S256", "test-header");
		String[] chunks = wlaToken.split("\\.");
		authChallenge.setChallenge(IdentityProviderUtil.b64Encode(headerJson.toJSONString())+"."+chunks[1]+"."+chunks[2]);

		PublicKeyRegistry publicKeyRegistry = new PublicKeyRegistry("id-hash", "WLA", "test-psu-token", clientJWK.toJSONString(),
				LocalDateTime.now().plusDays(4), "test-binding-id", "test-public-key-hash","thumbprint",
				getPemData(certificate), LocalDateTime.now());
		when(publicKeyRegistryRepository.findByIdHashAndAuthFactorInAndExpiredtimesGreaterThan(anyString(), any(), any()))
				.thenReturn(Arrays.asList(publicKeyRegistry));
		when(keymanagerUtil.convertToCertificate(anyString())).thenReturn(certificate);

		try {
			bindingValidatorServiceImpl.validateBindingAuth(transactionId, individualId, Arrays.asList(authChallenge));
			Assert.fail();
		} catch (KycAuthException e) {
			Assert.assertTrue(e.getErrorCode().equals(ErrorConstants.INVALID_CHALLENGE));
		}
	}

	@Test
	public void validateBinding_withoutSha256Thumbprint_thenFail() throws Exception {
		String transactionId = "909422113";
		String individualId  = "8267411571";
		AuthChallenge authChallenge = new AuthChallenge();
		authChallenge.setAuthFactorType("WLA");
		authChallenge.setFormat("jwt");

		X509Certificate certificate = getCertificate(clientJWK);
		String wlaToken = signJwt(individualId, certificate, false);
		authChallenge.setChallenge(wlaToken);

		PublicKeyRegistry publicKeyRegistry = new PublicKeyRegistry("id-hash", "WLA", "test-psu-token", clientJWK.toJSONString(),
				LocalDateTime.now().plusDays(4), "test-binding-id", "test-public-key-hash","thumbprint",
				getPemData(certificate), LocalDateTime.now());
		when(publicKeyRegistryRepository.findByIdHashAndAuthFactorInAndExpiredtimesGreaterThan(anyString(), any(), any()))
				.thenReturn(Arrays.asList(publicKeyRegistry));
		when(keymanagerUtil.convertToCertificate(anyString())).thenReturn(certificate);

		try {
			bindingValidatorServiceImpl.validateBindingAuth(transactionId, individualId, Arrays.asList(authChallenge));
			Assert.fail();
		} catch (KycAuthException e) {
			Assert.assertTrue(e.getErrorCode().equals(ErrorConstants.INVALID_CHALLENGE));
		}
	}

	@Test
	public void validateBinding_withUnBoundId_thenFail() throws EsignetException {
		when(publicKeyRegistryRepository.findByIdHashAndAuthFactorInAndExpiredtimesGreaterThan(anyString(), any(), any())).thenReturn(Arrays.asList());

		String transactionId = "909422113";
		String individualId  = "8267411571";
		AuthChallenge authChallenge = new AuthChallenge();
		authChallenge.setAuthFactorType("WLA");
		authChallenge.setFormat("jwt");
		authChallenge.setChallenge("wlaToken");

		try {
			bindingValidatorServiceImpl.validateBindingAuth(transactionId, individualId, Arrays.asList(authChallenge));
			Assert.fail();
		} catch (KycAuthException e) {
			Assert.assertTrue(e.getErrorCode().equals(ErrorConstants.KEY_BINDING_NOT_FOUND));
		}
	}

	@Test
	public void validateBinding_withUnBoundAuthFactors_thenFail() throws EsignetException {
		PublicKeyRegistry publicKeyRegistry = new PublicKeyRegistry("id-hash", "WLA", "test-psu-token", clientJWK.toJSONString(),
				LocalDateTime.now().plusDays(4), "test-binding-id", "test-public-key-hash","thumbprint",
				"certificate", LocalDateTime.now());
		when(publicKeyRegistryRepository.findByIdHashAndAuthFactorInAndExpiredtimesGreaterThan(anyString(), any(), any())).thenReturn(Arrays.asList(publicKeyRegistry));

		String transactionId = "909422113";
		String individualId  = "8267411571";
		AuthChallenge authChallengeWLA = new AuthChallenge();
        authChallengeWLA.setAuthFactorType("WLA");
        authChallengeWLA.setFormat("jwt");
        authChallengeWLA.setChallenge("wlaToken");
        AuthChallenge authChallengeHWA = new AuthChallenge();
        authChallengeHWA.setAuthFactorType("HLA");
        authChallengeHWA.setFormat("jwt");
        authChallengeHWA.setChallenge("hlaToken");

		try {
			bindingValidatorServiceImpl.validateBindingAuth(transactionId, individualId, Arrays.asList(authChallengeWLA, authChallengeHWA));
			Assert.fail();
		} catch (KycAuthException e) {
			Assert.assertTrue(e.getErrorCode().equals(ErrorConstants.UNBOUND_AUTH_FACTOR));
		}
	}

	@Test
	public void validateBinding_withInvalidChallenge_thenFail() throws EsignetException {
        PublicKeyRegistry publicKeyRegistry = new PublicKeyRegistry("id-hash", "WLA", "test-psu-token", clientJWK.toJSONString(),
                LocalDateTime.now().plusDays(4), "test-binding-id", "test-public-key-hash","certificate",
                "certificate", LocalDateTime.now());
        when(publicKeyRegistryRepository.findByIdHashAndAuthFactorInAndExpiredtimesGreaterThan(anyString(), any(), any())).thenReturn(Arrays.asList(publicKeyRegistry));

		String transactionId = "909422113";
		String individualId  = "8267411571";
		AuthChallenge authChallenge = new AuthChallenge();
		authChallenge.setAuthFactorType("WLA");
		authChallenge.setFormat("jwt");
		authChallenge.setChallenge("wlaToken");

		try {
			bindingValidatorServiceImpl.validateBindingAuth(transactionId, individualId, Arrays.asList(authChallenge));
			Assert.fail();
		} catch (KycAuthException e) {
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
		payload.put("aud", audienceId);
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
