package io.mosip.esignet.services;

import com.apicatalog.jsonld.document.JsonDocument;

import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.JWTClaimsSetVerifier;
import foundation.identity.jsonld.ConfigurableDocumentLoader;
import foundation.identity.jsonld.JsonLDObject;
import info.weboftrust.ldsignatures.LdProof;
import info.weboftrust.ldsignatures.canonicalizer.URDNA2015Canonicalizer;
import io.mosip.esignet.core.constants.Constants;
import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.dto.vci.CredentialRequest;
import io.mosip.esignet.core.dto.vci.CredentialResponse;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.core.exception.NotAuthenticatedException;
import io.mosip.esignet.core.spi.VCIssuanceService;
import io.mosip.esignet.core.util.IdentityProviderUtil;
import io.mosip.kernel.core.util.CryptoUtil;
import io.mosip.kernel.signature.dto.JWTSignatureRequestDto;
import io.mosip.kernel.signature.dto.JWTSignatureResponseDto;
import io.mosip.kernel.signature.service.SignatureService;
import lombok.extern.slf4j.Slf4j;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
public class VCIssuanceServiceImpl implements VCIssuanceService {

    @Value("#{${mosip.esignet.vci.key-values}}")
    private Map<String, Object> issuerMetadata;

    @Value("${config.server.file.storage.uri:}")
    private String configServerFileStorageURL; //TODO Remove this later

    @Value("#{${mosip.esignet.vc.context-url-map}}")
    private Map<String, String> contextUrlMap; //TODO Remove this later

    @Value("#{${mosip.esignet.discovery.key-values}}")
    private Map<String, Object> discoveryMap; //TODO Remove this later

    @Autowired
    private VCIUtilService vciUtilService;

    @Autowired
    private SignatureService signatureService; //TODO Remove this later

    private static Set<String> REQUIRED_ACCESS_TOKEN_CLAIMS;

    private ConfigurableDocumentLoader confDocumentLoader = null;

    static {
        REQUIRED_ACCESS_TOKEN_CLAIMS = new HashSet<>();
        REQUIRED_ACCESS_TOKEN_CLAIMS.add("sub");
        REQUIRED_ACCESS_TOKEN_CLAIMS.add("aud");
        REQUIRED_ACCESS_TOKEN_CLAIMS.add("exp");
        REQUIRED_ACCESS_TOKEN_CLAIMS.add("iss");
        REQUIRED_ACCESS_TOKEN_CLAIMS.add("iat");
    }

    @PostConstruct
    public void init() { //TODO Remove this later
        Map<URI, JsonDocument> jsonDocumentCacheMap = new HashMap<URI, JsonDocument> ();
        contextUrlMap.keySet().stream().forEach(contextUrl -> {
            String localConfigUrl = contextUrlMap.get(contextUrl);
            JsonDocument jsonDocument = vciUtilService.readJsonLDDocument(configServerFileStorageURL+localConfigUrl);
            try {
                jsonDocumentCacheMap.put(new URI(contextUrl), jsonDocument);
            } catch (URISyntaxException e) {
                log.warn("Verifiable Credential URI not able to add to cacheMap.");
            }
        });
        confDocumentLoader = new ConfigurableDocumentLoader(jsonDocumentCacheMap);
        confDocumentLoader.setEnableHttps(false);
        confDocumentLoader.setEnableHttp(false);
        confDocumentLoader.setEnableFile(false);
        log.info("Added cache for the list of configured URL Map: " + jsonDocumentCacheMap.keySet());
    }

    @Override
    public CredentialResponse getCredential(String authorizationHeader, CredentialRequest credentialRequest) {
        validateBearerToken(authorizationHeader);

        JsonLDObject vcJsonLdObject = null;
        try {
            vcJsonLdObject = buildDummyJsonLDWithLDProof();
        } catch (Exception e) {
            log.error("Failed to build mock VC", e);
            throw new EsignetException(ErrorConstants.UNKNOWN_ERROR);
        }
        log.info("Verifiable Credential Generation completed for the provided data.");
        CredentialResponse<JSONObject> credentialResponse = new CredentialResponse<>();
        credentialResponse.setCredential(new JSONObject(vcJsonLdObject.toMap()));
        credentialResponse.setFormat("ldp_vc");
        return credentialResponse;
    }

    @Override
    public Map<String, Object> getCredentialIssuerMetadata() {
        return issuerMetadata;
    }

    private JsonLDObject buildDummyJsonLDWithLDProof() throws Exception {
        Map<String, Object> formattedMap = new HashMap<>();
        formattedMap.put("id", "did:example:456");
        formattedMap.put("name", "John Doe");
        formattedMap.put("age", 30);

        Map<String, Object> verCredJsonObject = new HashMap<>();
        verCredJsonObject.put("@context", "https://www.w3.org/2018/credentials/v1");
        verCredJsonObject.put("type", Arrays.asList("VerifiableCredential"));
        verCredJsonObject.put("id", "urn:uuid:3978344f-8596-4c3a-a978-8fcaba3903c5");
        verCredJsonObject.put("issuer", "did:example:123");
        verCredJsonObject.put("issuanceDate", IdentityProviderUtil.getUTCDateTime());
        verCredJsonObject.put("credentialSubject", formattedMap);

        JsonLDObject vcJsonLdObject = JsonLDObject.fromJsonObject(verCredJsonObject);
        vcJsonLdObject.setDocumentLoader(confDocumentLoader);
        // vc proof
        Date created = Date.from(LocalDateTime.parse((String)verCredJsonObject.get("issuanceDate"),
                        DateTimeFormatter.ofPattern(Constants.UTC_DATETIME_PATTERN))
                .atZone(ZoneId.systemDefault()).toInstant());
        LdProof vcLdProof = LdProof.builder()
                .defaultContexts(false)
                .defaultTypes(false)
                .type("RsaSignature2018")
                .created(created)
                .proofPurpose("assertionMethod")
                .verificationMethod(new URI((String) discoveryMap.get("jwks_uri")))
                .build();

        URDNA2015Canonicalizer canonicalizer =	new URDNA2015Canonicalizer();
        byte[] vcSignBytes = canonicalizer.canonicalize(vcLdProof, vcJsonLdObject);
        String vcEncodedData = CryptoUtil.encodeToURLSafeBase64(vcSignBytes);

        JWTSignatureRequestDto jwtSignatureRequestDto = new JWTSignatureRequestDto();
        jwtSignatureRequestDto.setApplicationId(Constants.OIDC_SERVICE_APP_ID);
        jwtSignatureRequestDto.setReferenceId("");
        jwtSignatureRequestDto.setIncludePayload(false);
        jwtSignatureRequestDto.setIncludeCertificate(true);
        jwtSignatureRequestDto.setIncludeCertHash(true);
        jwtSignatureRequestDto.setDataToSign(vcEncodedData);
        JWTSignatureResponseDto responseDto = signatureService.jwtSign(jwtSignatureRequestDto);

        LdProof ldProofWithJWS = LdProof.builder()
                .base(vcLdProof)
                .defaultContexts(false)
                .jws(responseDto.getJwtSignedData())
                .build();
        ldProofWithJWS.addToJsonLDObject(vcJsonLdObject);
        return vcJsonLdObject;
    }

    private void validateBearerToken(String authorizationHeader)  {
        if(authorizationHeader == null || authorizationHeader.isBlank())
            throw new NotAuthenticatedException();

        String[] tokenParts = IdentityProviderUtil.splitAndTrimValue(authorizationHeader, Constants.SPACE);
        if(tokenParts.length <= 1)
            throw new NotAuthenticatedException();

        if(!Constants.BEARER.equals(tokenParts[0]))
            throw new NotAuthenticatedException();

        //TODO validate signature, need to handle both esignet as IDP and also other Idp's

        try {
            JWT jwt = JWTParser.parse(tokenParts[1]);
            JWTClaimsSetVerifier claimsSetVerifier = new DefaultJWTClaimsVerifier(new JWTClaimsSet.Builder()
                    //.audience(clientId)
                    //.issuer(issuerId)
                    //.subject(subject)
                    .build(), REQUIRED_ACCESS_TOKEN_CLAIMS);
            claimsSetVerifier.verify(jwt.getJWTClaimsSet(), null);
        } catch (Exception e) {
            log.error("Access token claims verification failed", e);
            throw new NotAuthenticatedException();
        }
    }
}