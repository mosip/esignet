package io.mosip.esignet.services;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.JWTClaimsSet;
import io.mosip.esignet.api.dto.ClaimDetail;
import io.mosip.esignet.api.dto.Claims;
import io.mosip.esignet.core.dto.*;
import io.mosip.esignet.core.exception.InvalidTransactionException;
import io.mosip.esignet.core.spi.ConsentService;
import io.mosip.esignet.core.util.KafkaHelperService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ConsentHelperService {

    @Autowired
    private CacheUtilService cacheUtilService;

    @Autowired
    private ConsentService consentService;

    @Autowired
    private KafkaHelperService kafkaHelperService;

    @Value("${mosip.esignet.kafka.linked-auth-code.topic}")
    private String linkedAuthCodeTopicName;

    public void processConsent(String transactionId) {
        OIDCTransaction transaction = cacheUtilService.getAuthenticatedTransaction(transactionId);
        if(transaction == null)
            throw new InvalidTransactionException();
        UserConsentRequest userConsentRequest = new UserConsentRequest();
        userConsentRequest.setClientId(transaction.getClientId());
        userConsentRequest.setPsuToken(transaction.getPartnerSpecificUserToken());
        Optional<ConsentDetail> consent = consentService.getUserConsent(userConsentRequest);

        ConsentAction consentAction = consent.isEmpty() ? ConsentAction.CAPTURE : evaluateConsentAction(transaction,consent.get());

        transaction.setConsentAction(consentAction);

        if(consentAction.equals(ConsentAction.NOCAPTURE)) {
            parseConsent(transaction, consent);
        }
        cacheUtilService.setAuthenticatedTransaction(transactionId,transaction);
    }

    private static void parseConsent(OIDCTransaction transaction, Optional<ConsentDetail> consent) {
        ConsentDetail userConsentDetail = consent.get();
        log.debug("consent {}", userConsentDetail);
        List<String> acceptedClaims = userConsentDetail.getClaims().getUserinfo().entrySet().stream().
                filter(entry -> entry != null && entry.getValue() != null && entry.getValue().isAccepted())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        List<String> permittedScopes = userConsentDetail.getAuthorizationScopes().entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        transaction.setAcceptedClaims(acceptedClaims);
        transaction.setPermittedScopes(permittedScopes);
    }

    public LinkedKycAuthResponseV2 processLinkedConsent(String linkedAuthTransactionId) {
        OIDCTransaction transaction = cacheUtilService.getLinkedAuthTransaction(linkedAuthTransactionId);
        if(transaction == null)
            throw new InvalidTransactionException();
        UserConsentRequest userConsentRequest = new UserConsentRequest();
        userConsentRequest.setClientId(transaction.getClientId());
        userConsentRequest.setPsuToken(transaction.getPartnerSpecificUserToken());
        Optional<ConsentDetail> consent = consentService.getUserConsent(userConsentRequest);

        ConsentAction consentAction =  ConsentAction.CAPTURE;
        if(consent.isPresent()) {
            consentAction = evaluateConsentAction(transaction,consent.get());
            if(consentAction.equals(ConsentAction.NOCAPTURE)){
                if(!validateSignature(transaction,consent.get())){
                    consentAction = ConsentAction.CAPTURE;
                }
            }
        }
        transaction.setConsentAction(consentAction);

        if(consentAction.equals(ConsentAction.NOCAPTURE)) {
            parseConsent(transaction, consent);
        }
        cacheUtilService.setLinkedAuthenticatedTransaction(linkedAuthTransactionId,transaction);
        if(consentAction.equals(ConsentAction.NOCAPTURE)){
            cacheUtilService.setLinkedConsentedTransaction(linkedAuthTransactionId, transaction);
            kafkaHelperService.publish(linkedAuthCodeTopicName, linkedAuthTransactionId);
        }
        LinkedKycAuthResponseV2 linkedKycAuthResponseV2 = new LinkedKycAuthResponseV2();
        linkedKycAuthResponseV2.setLinkedTransactionId(linkedAuthTransactionId);
        linkedKycAuthResponseV2.setConsentAction(consentAction);
        return linkedKycAuthResponseV2;
    }

    public void addUserConsent(String transactionId, boolean linked, String signature) {
        OIDCTransaction transaction;
        if(!linked) {
            transaction = cacheUtilService.getUserConsentedTransaction(transactionId);
        } else {
            transaction = cacheUtilService.getLinkedConsentedTransaction(transactionId);
        }
        if(transaction == null)
            throw new InvalidTransactionException();
        if(ConsentAction.CAPTURE.equals(transaction.getConsentAction())){
            UserConsent userConsent = new UserConsent();
            userConsent.setClientId(transaction.getClientId());
            userConsent.setPsuToken(transaction.getPartnerSpecificUserToken());
            Claims claims = transaction.getRequestedClaims();
            List<String> acceptedClaims = transaction.getAcceptedClaims();
            setIsAcceptedBasedOnClaims(acceptedClaims,claims);
            userConsent.setClaims(claims);
            userConsent.setSignature(signature);
            List<String> permittedScopes = transaction.getPermittedScopes();
            List<String> authorizeScope = transaction.getRequestedAuthorizeScopes();
            Map<String, Boolean> authorizeScopes = permittedScopes != null ? permittedScopes.stream()
                    .collect(Collectors.toMap(Function.identity(), authorizeScope::contains)) : Collections.emptyMap();
            userConsent.setAuthorizationScopes(authorizeScopes);
            consentService.saveUserConsent(userConsent);
        }
    }

    private void setIsAcceptedBasedOnClaims(List<String> acceptedClaims, Claims requestedClaims) {
        Map<String ,ClaimDetail> userInfo = requestedClaims.getUserinfo();
        for (String acceptedClaim : acceptedClaims) {
            if (userInfo.containsKey(acceptedClaim)) {
                ClaimDetail claimDetail = userInfo.get(acceptedClaim);
                if(claimDetail!= null){
                    claimDetail.setAccepted(true);
                } else {
                    claimDetail = new ClaimDetail();
                    claimDetail.setAccepted(true);
                }
                userInfo.put(acceptedClaim, claimDetail);
            }
        }
    }


    private static ConsentAction evaluateConsentAction(OIDCTransaction transaction, ConsentDetail consentDetail) {
        if(consentDetail == null) {
            return ConsentAction.CAPTURE;
        }
        //validate requested claims
        Claims requestedClaims = transaction.getRequestedClaims();
        List<String> requestedScopes = transaction.getRequestedAuthorizeScopes();

        if((requestedClaims == null ||
                (requestedClaims.getId_token().isEmpty() && requestedClaims.getUserinfo().isEmpty()))
                && requestedScopes.isEmpty()

        ) {
            return ConsentAction.NOCAPTURE;
        }

        //validate consented claims
        if(requestedClaims!= null && consentDetail.getClaims() != null &&!requestedClaims.isEqualToIgnoringAccepted(consentDetail.getClaims())){
            return ConsentAction.CAPTURE;
        }

        //validate consented scopes
        Set<String> acceptedScopes = consentDetail.getAuthorizationScopes()
                .entrySet()
                .stream().filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        if(!requestedScopes.isEmpty()
                && ( !new HashSet<>(requestedScopes).containsAll(consentDetail.getAuthorizationScopes().keySet()) ||
                !new HashSet<>(requestedScopes).containsAll(acceptedScopes)
        )){
            return ConsentAction.CAPTURE;
        }
        return ConsentAction.NOCAPTURE;
    }

    private boolean validateSignature(OIDCTransaction transaction, ConsentDetail consentDetail){
        return true;

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