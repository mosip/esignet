package io.mosip.esignet.services;

import com.auth0.jwt.interfaces.Claim;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.JWTClaimsSet;
import io.mosip.esignet.api.dto.ClaimDetail;
import io.mosip.esignet.api.dto.Claims;
import io.mosip.esignet.api.util.ConsentAction;
import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.dto.*;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.core.exception.InvalidTransactionException;
import io.mosip.esignet.core.spi.ConsentService;
import io.mosip.esignet.core.util.IdentityProviderUtil;
import io.mosip.esignet.core.util.KafkaHelperService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.mosip.esignet.core.util.IdentityProviderUtil.ALGO_SHA3_256;

@Slf4j
@Component
public class ConsentHelperService {

    @Autowired
    private CacheUtilService cacheUtilService;

    @Autowired
    private ConsentService consentService;

    private final ObjectMapper objectMapper=new ObjectMapper();

    @Autowired
    private KafkaHelperService kafkaHelperService;

    @Value("${mosip.esignet.kafka.linked-auth-code.topic}")
    private String linkedAuthCodeTopicName;

    public void processConsent(OIDCTransaction transaction) {
        UserConsentRequest userConsentRequest = new UserConsentRequest();
        userConsentRequest.setClientId(transaction.getClientId());
        userConsentRequest.setPsuToken(transaction.getPartnerSpecificUserToken());
        Optional<ConsentDetail> consent = consentService.getUserConsent(userConsentRequest);

        ConsentAction consentAction = consent.isEmpty() ? ConsentAction.CAPTURE : evaluateConsentAction(transaction,consent.get());

        transaction.setConsentAction(consentAction);

        if(consentAction.equals(ConsentAction.NOCAPTURE)) {
            transaction.setAcceptedClaims(consent.get().getAcceptedClaims());
            transaction.setPermittedScopes(consent.get().getPermittedScopes());
        }
    }


    public void addUserConsent(OIDCTransaction transaction, boolean linked, String signature) {
        if(ConsentAction.CAPTURE.equals(transaction.getConsentAction())){
            UserConsent userConsent = new UserConsent();
            userConsent.setClientId(transaction.getClientId());
            userConsent.setPsuToken(transaction.getPartnerSpecificUserToken());
            Claims claims = transaction.getRequestedClaims();
            List<String> acceptedClaims = transaction.getAcceptedClaims();
            Claims normalizedClaims = new Claims();
            normalizedClaims.setUserinfo(normalizeClaims(claims.getUserinfo()));
            normalizedClaims.setId_token(claims.getId_token());

            userConsent.setClaims(normalizedClaims);
            userConsent.setSignature(signature);
            List<String> permittedScopes = transaction.getPermittedScopes();
            List<String> authorizeScope = transaction.getRequestedAuthorizeScopes();
            Map<String, Boolean> authorizeScopes = permittedScopes != null ? permittedScopes.stream()
                    .collect(Collectors.toMap(Function.identity(), authorizeScope::contains)) : Collections.emptyMap();
            userConsent.setAuthorizationScopes(authorizeScopes);
            userConsent.setAcceptedClaims(acceptedClaims);
            userConsent.setPermittedScopes(permittedScopes);
            try {
                userConsent.setHash(hashUserConsent(normalizedClaims, authorizeScopes));
            } catch (JsonProcessingException e) {
                throw new EsignetException(ErrorConstants.INVALID_CLAIM);
            }
            consentService.saveUserConsent(userConsent);
        }
    }


    public Map<String, ClaimDetail> normalizeClaims(Map<String, ClaimDetail> claims){
        return claims.entrySet().stream().collect(
                Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> {
                            ClaimDetail claimDetail = entry.getValue();
                            return claimDetail == null ? new ClaimDetail() : claimDetail;
                        }
                )
        );
    }

    public String hashUserConsent(Claims claims,Map<String, Boolean> authorizeScopes) throws JsonProcessingException {

        Map<String,Object> claimsAndAuthorizeScopes = new LinkedHashMap<>();

        List<Map.Entry<String, ClaimDetail>> entryList;
        Map<String, ClaimDetail> sortedMap = new LinkedHashMap<>();
        if(claims.getUserinfo()!=null){
            entryList = new ArrayList<>(claims.getUserinfo().entrySet());
            sortClaims(entryList, sortedMap);

        }
        //Now for sorting  id_token
        if(claims.getId_token()!=null)
        {
            entryList = new ArrayList<>(claims.getId_token().entrySet());
            sortClaims(entryList, sortedMap);

        }
        //Now for authorizeScopes
        Map<String,Boolean> sortedAuthorzeScopeMap=new LinkedHashMap<>();

        List<Map.Entry<String,Boolean>>authorizeScopesList = new ArrayList<>(authorizeScopes.entrySet());
        authorizeScopesList.sort(new Comparator<Map.Entry<String, Boolean>>() {
            public int compare(Map.Entry<String, Boolean> o1, Map.Entry<String, Boolean> o2) {
                return o1.getKey().compareTo(o2.getKey());
            }
        });
        for (Map.Entry<String, Boolean> entry : authorizeScopesList) {
            sortedAuthorzeScopeMap.put(entry.getKey(), entry.getValue());
        }
        claimsAndAuthorizeScopes.put("claims",sortedMap);
        claimsAndAuthorizeScopes.put("authorizeScopes",sortedAuthorzeScopeMap);
        String s=claimsAndAuthorizeScopes.toString().trim().replace(" ","");
        return IdentityProviderUtil.generateB64EncodedHash(ALGO_SHA3_256, s);
    }

    private static void sortClaims(List<Map.Entry<String, ClaimDetail>> entryList, Map<String, ClaimDetail> sortedMap) {
        entryList.sort(new Comparator<>() {
            public int compare(Map.Entry<String, ClaimDetail> o1, Map.Entry<String, ClaimDetail> o2) {
                return o1.getKey().compareTo(o2.getKey());
            }
        });
        for (Map.Entry<String, ClaimDetail> entry : entryList) {
            sortedMap.put(entry.getKey(), entry.getValue());
        }
    }


    private ConsentAction evaluateConsentAction(OIDCTransaction transaction, ConsentDetail consentDetail) {
        String hash;
        try {
            List<String> permittedScopes = transaction.getPermittedScopes();
            List<String> authorizeScope = transaction.getRequestedAuthorizeScopes();
            Map<String, Boolean> authorizeScopes = permittedScopes != null ? permittedScopes.stream()
                    .collect(Collectors.toMap(Function.identity(), authorizeScope::contains)) : Collections.emptyMap();
            hash = hashUserConsent(transaction.getRequestedClaims(),authorizeScopes );
        } catch (JsonProcessingException e) {
            throw new EsignetException(ErrorConstants.INVALID_CLAIM);
        }
        return consentDetail.getHash().equals(hash) ? ConsentAction.NOCAPTURE : ConsentAction.CAPTURE;
    }

    private String generateSignedObject(OIDCTransaction transaction, ConsentDetail consentDetail){
        List<String> acceptedClaims = transaction.getAcceptedClaims();
        List<String> permittedScopes = transaction.getPermittedScopes();
        Collections.sort(acceptedClaims);
        Collections.sort(acceptedClaims);
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .claim("acceptedClaims", acceptedClaims)
                .claim("authorizeScopes", permittedScopes)
                .build();
        String jws = consentDetail.getSignature();
        String[] parts = jws.split("\\.");

        String header = parts[0];
        String signature = parts[1];
        JWSHeader jwsHeader = new JWSHeader(JWSAlgorithm.parse(header));
        JWSObject jwsObject = null;
        try {
            jwsObject = new JWSObject(jwsHeader.toBase64URL(), Base64URL.encode(claimsSet.toJSONObject().toJSONString())
                    ,Base64URL.encode(signature) );
        } catch (ParseException e) {

        }
        return jwsObject == null ? "": jwsObject.serialize();
    }

}