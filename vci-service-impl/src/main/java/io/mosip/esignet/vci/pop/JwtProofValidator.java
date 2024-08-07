/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.vci.pop;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.dto.vci.CredentialProof;
import io.mosip.esignet.core.exception.InvalidRequestException;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.util.*;

@Slf4j
@Component
public class JwtProofValidator implements ProofValidator {

    private static final String HEADER_TYP = "openid4vci-proof+jwt";
    private static final String DID_JWK_PREFIX = "did:jwk:";

    @Value("#{${mosip.esignet.vci.supported.jwt-proof-alg}}")
    private List<String> supportedAlgorithms;

    @Value("${mosip.esignet.vci.identifier}")
    private String credentialIdentifier;

    @Override
    public String getProofType() {
        return "jwt";
    }

    private static final Set<JWSAlgorithm> allowedSignatureAlgorithms;

    private static Set<String> REQUIRED_CLAIMS;

    static {
        allowedSignatureAlgorithms = new HashSet<>();
        allowedSignatureAlgorithms.addAll(List.of(JWSAlgorithm.Family.SIGNATURE.toArray(new JWSAlgorithm[0])));

        REQUIRED_CLAIMS = new HashSet<>();
        REQUIRED_CLAIMS.add("aud");
        REQUIRED_CLAIMS.add("exp");
        REQUIRED_CLAIMS.add("iss");
        REQUIRED_CLAIMS.add("iat");
    }

    @Override
    public boolean validate(String clientId, String cNonce, CredentialProof credentialProof) {
        if(credentialProof.getJwt() == null || credentialProof.getJwt().isBlank()) {
            log.error("Found invalid jwt in the credential proof");
            return false;
        }

        try {
            SignedJWT jwt = (SignedJWT) JWTParser.parse(credentialProof.getJwt());
            validateHeaderClaims(jwt.getHeader());

            JWK jwk = getKeyFromHeader(jwt.getHeader());
            if(jwk.isPrivate()) {
                log.error("Provided key material contains private key! Rejecting proof.");
                throw new InvalidRequestException(ErrorConstants.PROOF_HEADER_INVALID_KEY);
            }

            DefaultJWTClaimsVerifier claimsSetVerifier = new DefaultJWTClaimsVerifier(new JWTClaimsSet.Builder()
                    .audience(credentialIdentifier)
                    .issuer(clientId)
                    .claim("nonce", cNonce)
                    .build(), REQUIRED_CLAIMS);
            claimsSetVerifier.setMaxClockSkew(0);

            JWSKeySelector keySelector = new JWSVerificationKeySelector(allowedSignatureAlgorithms,
                    new ImmutableJWKSet(new JWKSet(jwk)));
            ConfigurableJWTProcessor jwtProcessor = new DefaultJWTProcessor();
            jwtProcessor.setJWSKeySelector(keySelector);
            jwtProcessor.setJWSTypeVerifier(new DefaultJOSEObjectTypeVerifier(new JOSEObjectType(HEADER_TYP)));
            jwtProcessor.setJWTClaimsSetVerifier(claimsSetVerifier);
            jwtProcessor.process(credentialProof.getJwt(), null);
            return true;
        } catch (InvalidRequestException e) {
            log.error("Invalid proof : {}", e.getErrorCode());
        }  catch (ParseException e) {
            log.error("Failed to parse jwt in the credential proof", e);
        } catch (BadJOSEException | JOSEException e) {
            log.error("JWT proof verification failed", e);
        }
        return false;
    }

    @Override
    public String getKeyMaterial(CredentialProof credentialProof) {
        try {
            SignedJWT jwt = (SignedJWT) JWTParser.parse(credentialProof.getJwt());
            JWK jwk = getKeyFromHeader(jwt.getHeader());
            byte[] keyBytes = jwk.toJSONString().getBytes(StandardCharsets.UTF_8);
            return DID_JWK_PREFIX.concat(Base64.getUrlEncoder().encodeToString(keyBytes));
        } catch (ParseException e) {
            log.error("Failed to parse jwt in the credential proof", e);
        }
        throw new InvalidRequestException(ErrorConstants.PROOF_HEADER_INVALID_KEY);
    }

    private void validateHeaderClaims(JWSHeader jwsHeader) {
        if(Objects.isNull(jwsHeader.getType()) || !HEADER_TYP.equals(jwsHeader.getType().getType()))
            throw new InvalidRequestException(ErrorConstants.PROOF_HEADER_INVALID_TYP);

        if(Objects.isNull(jwsHeader.getAlgorithm()) || !supportedAlgorithms.contains(jwsHeader.getAlgorithm().getName()))
            throw new InvalidRequestException(ErrorConstants.PROOF_HEADER_INVALID_ALG);

        if(Objects.isNull(jwsHeader.getKeyID()) && Objects.isNull(jwsHeader.getJWK()))
            throw new InvalidRequestException(ErrorConstants.PROOF_HEADER_INVALID_KEY);

        //both cannot be present, either one of them is only allowed
        if(Objects.nonNull(jwsHeader.getKeyID()) && Objects.nonNull(jwsHeader.getJWK()))
            throw new InvalidRequestException(ErrorConstants.PROOF_HEADER_AMBIGUOUS_KEY);

        //TODO x5c and trust_chain validation
    }

    private JWK getKeyFromHeader(JWSHeader jwsHeader) {
        if(Objects.nonNull(jwsHeader.getJWK()))
            return jwsHeader.getJWK();

        return resolveDID(jwsHeader.getKeyID());
    }

    /**
     * Currently only handles did:jwk, Need to handle other methods
     * @param keyId
     * @return
     */
    private JWK resolveDID(String did) {
        if(did.startsWith(DID_JWK_PREFIX)) {
            try {
                //Ignoring fragment part as did:jwk only contains single key, the DID URL fragment identifier is always
                //a fixed #0 value. If the JWK contains a kid value it is not used as the reference, #0 is the only valid value.
                did = did.split("#")[0];
                byte[] jwkBytes = Base64.getUrlDecoder().decode(did.substring(DID_JWK_PREFIX.length()));
                org.json.JSONObject jsonKey = new org.json.JSONObject(new String(jwkBytes));
                jsonKey.put("kid", did);
                return JWK.parse(jsonKey.toString());
            } catch (IllegalArgumentException e) {
                log.error("Invalid base64 encoded ID : {}", did, e);
            } catch (ParseException | JSONException e) {
                log.error("Invalid jwk : {}", did, e);
            }
        }
        throw new InvalidRequestException(ErrorConstants.PROOF_HEADER_INVALID_KEY);
    }
}
