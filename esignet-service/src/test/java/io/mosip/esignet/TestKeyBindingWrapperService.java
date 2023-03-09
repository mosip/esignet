package io.mosip.esignet;

import com.nimbusds.jose.jwk.RSAKey;
import io.mosip.esignet.api.dto.AuthChallenge;
import io.mosip.esignet.api.dto.KeyBindingResult;
import io.mosip.esignet.api.dto.SendOtpResult;
import io.mosip.esignet.api.exception.KeyBindingException;
import io.mosip.esignet.api.exception.SendOtpException;
import io.mosip.esignet.api.spi.KeyBinder;
import io.mosip.kernel.core.util.DateUtils;
import io.mosip.kernel.keymanagerservice.dto.KeyPairGenerateRequestDto;
import io.mosip.kernel.keymanagerservice.dto.SignatureCertificate;
import io.mosip.kernel.keymanagerservice.service.KeymanagerService;
import lombok.extern.slf4j.Slf4j;
import net.minidev.json.JSONObject;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.x509.X509V3CertificateGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.security.auth.x500.X500Principal;
import java.io.StringWriter;
import java.math.BigInteger;
import java.security.PrivateKey;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@ConditionalOnProperty(value = "mosip.esignet.integration.key-binder", havingValue = "TestKeyBindingWrapperService")
@Component
@Slf4j
public class TestKeyBindingWrapperService implements KeyBinder {

    public static final String BINDING_SERVICE_APP_ID = "MOCK_BINDING_SERVICE";

    @Autowired
    private KeymanagerService keymanagerService;

    @Value("${mosip.esignet.binding.key-expire-days}")
    private int expireInDays;

    private static final Map<String, List<String>> supportedFormats = new HashMap<>();

    static {
        supportedFormats.put("OTP", Arrays.asList("alpha-numeric"));
        supportedFormats.put("PIN", Arrays.asList("number"));
        supportedFormats.put("BIO", Arrays.asList("encoded-json"));
        supportedFormats.put("WLA", Arrays.asList("jwt"));
    }


    @Override
    public SendOtpResult sendBindingOtp(String individualId, List<String> otpChannels,
                                        Map<String, String> requestHeaders) throws SendOtpException {
        SendOtpResult sendOtpResult = new SendOtpResult(null,"mock", "mock");
        //TODO
        return sendOtpResult;
    }

    @Override
    public KeyBindingResult doKeyBinding(String individualId, List<AuthChallenge> challengeList, Map<String, Object> publicKeyJWK,
                                         String bindAuthFactorType, Map<String, String> requestHeaders) throws KeyBindingException {
        KeyBindingResult keyBindingResult = new KeyBindingResult();
        //TODO
        //create a signed certificate, with cn as username
        //certificate validity based on configuration
        try {
            RSAKey rsaKey = RSAKey.parse(new JSONObject(publicKeyJWK));
            X509V3CertificateGenerator generator = new X509V3CertificateGenerator();
            generator.setSubjectDN(new X500Principal("CN=Mock-user-name"));
            generator.setIssuerDN( new X500Principal("CN=Mock-IDA"));
            LocalDateTime notBeforeDate = DateUtils.getUTCCurrentDateTime();
            LocalDateTime notAfterDate = notBeforeDate.plus(expireInDays, ChronoUnit.DAYS);
            generator.setNotBefore(Timestamp.valueOf(notBeforeDate));
            generator.setNotAfter(Timestamp.valueOf(notAfterDate));
            generator.setPublicKey(rsaKey.toPublicKey());
            generator.setSignatureAlgorithm("SHA256WITHRSA");
            generator.setSerialNumber(new BigInteger(String.valueOf(System.currentTimeMillis())));

            setupMockBindingKey();
            SignatureCertificate signatureCertificate = keymanagerService.getSignatureCertificate(BINDING_SERVICE_APP_ID, Optional.empty(),
                    DateUtils.getUTCCurrentDateTimeString());
            PrivateKey privateKey = signatureCertificate.getCertificateEntry().getPrivateKey();
            StringWriter stringWriter = new StringWriter();
            try (JcaPEMWriter pemWriter = new JcaPEMWriter(stringWriter)) {
                pemWriter.writeObject(generator.generate(privateKey));
                pemWriter.flush();
                keyBindingResult.setCertificate(stringWriter.toString());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        keyBindingResult.setPartnerSpecificUserToken(individualId);
        return keyBindingResult;
    }

    private void setupMockBindingKey() {
        KeyPairGenerateRequestDto mockBindingKeyRequest = new KeyPairGenerateRequestDto();
        mockBindingKeyRequest.setApplicationId(BINDING_SERVICE_APP_ID);
        keymanagerService.generateMasterKey("CSR", mockBindingKeyRequest);
        log.info("===================== MOCK_BINDING_SERVICE KEY SETUP COMPLETED ========================");
    }

    @Override
    public List<String> getSupportedChallengeFormats(String authFactorType) {
        return supportedFormats.getOrDefault(authFactorType, Arrays.asList());
    }
}
