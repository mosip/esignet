/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.mosip.esignet.api.dto.ClaimDetail;
import io.mosip.esignet.api.dto.Claims;
import io.mosip.esignet.api.spi.AuditPlugin;
import io.mosip.esignet.api.util.Action;
import io.mosip.esignet.api.util.ActionStatus;
import io.mosip.esignet.api.util.ConsentAction;
import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.dto.ConsentDetail;
import io.mosip.esignet.core.dto.OIDCTransaction;
import io.mosip.esignet.core.dto.UserConsent;
import io.mosip.esignet.core.dto.UserConsentRequest;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.core.spi.ConsentService;
import io.mosip.esignet.core.util.AuditHelper;
import io.mosip.esignet.core.util.IdentityProviderUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static io.mosip.esignet.core.util.IdentityProviderUtil.ALGO_SHA3_256;

@Slf4j
@Component
public class ConsentHelperService {
    @Autowired
    private ConsentService consentService;

    @Autowired
    private AuditPlugin auditWrapper;

    public void processConsent(OIDCTransaction transaction, boolean linked) {
        UserConsentRequest userConsentRequest = new UserConsentRequest();
        userConsentRequest.setClientId(transaction.getClientId());
        userConsentRequest.setPsuToken(transaction.getPartnerSpecificUserToken());
        Optional<ConsentDetail> consent = consentService.getUserConsent(userConsentRequest);

        if(CollectionUtils.isEmpty(transaction.getVoluntaryClaims())
                && CollectionUtils.isEmpty(transaction.getEssentialClaims())
                && CollectionUtils.isEmpty(transaction.getRequestedAuthorizeScopes())){
            transaction.setConsentAction(ConsentAction.NOCAPTURE);
            transaction.setAcceptedClaims(List.of());
            transaction.setPermittedScopes(List.of());
        } else {
            ConsentAction consentAction = consent.isEmpty() ? ConsentAction.CAPTURE : evaluateConsentAction(transaction, consent.get(), linked);

            transaction.setConsentAction(consentAction);

            if (consentAction.equals(ConsentAction.NOCAPTURE)) {
                transaction.setAcceptedClaims(consent.get().getAcceptedClaims()); //NOSONAR consent is already evaluated to be not null
                transaction.setPermittedScopes(consent.get().getPermittedScopes()); //NOSONAR consent is already evaluated to be not null
            }
        }
    }


    public void updateUserConsent(OIDCTransaction transaction, boolean linked, String signature) {
        if(ConsentAction.NOCAPTURE.equals(transaction.getConsentAction())
            && transaction.getEssentialClaims().isEmpty()
                && transaction.getVoluntaryClaims().isEmpty()
                && transaction.getRequestedAuthorizeScopes().isEmpty()
        ){
            //delete old consent if it exists since this scenario doesn't require capture of consent.
            consentService.deleteUserConsent(transaction.getClientId(),transaction.getPartnerSpecificUserToken());
            auditWrapper.logAudit(Action.DELETE_USER_CONSENT, ActionStatus.SUCCESS, AuditHelper.buildAuditDto(transaction.getTransactionId(),transaction),null);
        }
        if(ConsentAction.CAPTURE.equals(transaction.getConsentAction())){
            UserConsent userConsent = new UserConsent();
            userConsent.setClientId(transaction.getClientId());
            userConsent.setPsuToken(transaction.getPartnerSpecificUserToken());
            Claims claims = transaction.getRequestedClaims();
            List<String> acceptedClaims = transaction.getAcceptedClaims();
            Claims normalizedClaims = new Claims();
            normalizedClaims.setUserinfo(normalizeClaims(claims.getUserinfo()));
            normalizedClaims.setId_token(normalizeClaims(claims.getId_token()));

            userConsent.setClaims(normalizedClaims);
            userConsent.setSignature(signature);
            List<String> permittedScopes = transaction.getPermittedScopes();
            List<String> requestedAuthorizeScopes = transaction.getRequestedAuthorizeScopes();
            // defaulting the essential boolean flag as false
            Map<String, Boolean> authorizeScopes = requestedAuthorizeScopes != null ? requestedAuthorizeScopes.stream()
                    .collect(Collectors.toMap(Function.identity(), s->false)) : Collections.emptyMap();
            userConsent.setAuthorizationScopes(authorizeScopes);
            userConsent.setAcceptedClaims(acceptedClaims);
            userConsent.setPermittedScopes(permittedScopes);
            try {
                userConsent.setHash(hashUserConsent(normalizedClaims, authorizeScopes));
            } catch (JsonProcessingException e) {
                log.error("Failed to hash the user consent", e);
                throw new EsignetException(ErrorConstants.INVALID_CLAIM);
            }
            consentService.saveUserConsent(userConsent);
            auditWrapper.logAudit(Action.UPDATE_USER_CONSENT, ActionStatus.SUCCESS, AuditHelper.buildAuditDto(transaction.getTransactionId(),transaction),null);
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
            sortedMap.put(entry.getKey(), sortClaimDetail(entry.getValue()));
        }
    }

    private static ClaimDetail sortClaimDetail(ClaimDetail claimDetail){
        if(claimDetail!= null && claimDetail.getValues() != null) {
            Arrays.sort(claimDetail.getValues());
        }
        return claimDetail;
    }

    private ConsentAction evaluateConsentAction(OIDCTransaction transaction, ConsentDetail consentDetail, boolean linked) {
        String hash;
        try {
            List<String> authorizeScope = transaction.getRequestedAuthorizeScopes();
            // defaulting the essential boolean flag as false
            Map<String, Boolean> authorizeScopes = authorizeScope != null ? authorizeScope.stream()
                    .collect(Collectors.toMap(Function.identity(), s->false)) : Collections.emptyMap();
            Claims normalizedClaims = new Claims();
            normalizedClaims.setUserinfo(normalizeClaims(transaction.getRequestedClaims().getUserinfo()));
            normalizedClaims.setId_token(normalizeClaims(transaction.getRequestedClaims().getId_token()));
            hash = hashUserConsent(normalizedClaims, authorizeScopes);
        } catch (JsonProcessingException e) {
            log.error("Failed to hash the user consent", e);
            throw new EsignetException(ErrorConstants.INVALID_CLAIM);
        }
        return consentDetail.getHash().equals(hash) ? ConsentAction.NOCAPTURE : ConsentAction.CAPTURE;
    }
}