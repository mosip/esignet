/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.esignet.api.dto.claim.*;
import io.mosip.esignet.api.util.FilterCriteriaMatcher;
import io.mosip.esignet.core.constants.Constants;
import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.dto.ClaimStatus;
import io.mosip.esignet.core.dto.ClientDetail;
import io.mosip.esignet.core.dto.OAuthDetailRequest;
import io.mosip.esignet.core.dto.OIDCTransaction;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.core.util.IdentityProviderUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

import static io.mosip.esignet.core.constants.Constants.*;
import static io.mosip.esignet.core.constants.ErrorConstants.INVALID_ACCEPTED_CLAIM;

@Slf4j
@Component
public class ClaimsHelperService {


    @Value("#{${mosip.esignet.openid.scope.claims}}")
    private Map<String, List<String>> claims;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FilterCriteriaMatcher filterCriteriaMatcher;

    protected Map<String, List<String>> getClaimNames(Claims resolvedClaims) {
        List<String> essentialClaims = new ArrayList<>();
        List<String> voluntaryClaims = new ArrayList<>();
        for(Map.Entry<String, List<Map<String, Object>>> claim : resolvedClaims.getUserinfo().entrySet()) {
            if(claim.getValue() != null && claim.getValue().stream().anyMatch( m -> (boolean) m.getOrDefault("essential", false)))
                essentialClaims.add(claim.getKey());
            else
                voluntaryClaims.add(claim.getKey());
        }
        Map<String, List<String>> result = new HashMap<>();
        result.put(ESSENTIAL, essentialClaims);
        result.put(VOLUNTARY, voluntaryClaims);
        return result;
    }


    protected Claims resolveRequestedClaims(OAuthDetailRequest oauthDetailRequest, ClientDetail clientDetailDto)
            throws EsignetException {
        Claims resolvedClaims = new Claims();
        resolvedClaims.setUserinfo(new HashMap<>());
        resolvedClaims.setId_token(new HashMap<>());

        String[] requestedScopes = IdentityProviderUtil.splitAndTrimValue(oauthDetailRequest.getScope(), Constants.SPACE);
        ClaimsV2 requestedClaims = oauthDetailRequest.getClaims();
        boolean isRequestedUserInfoClaimsPresent = requestedClaims != null && requestedClaims.getUserinfo() != null;
        log.info("isRequestedUserInfoClaimsPresent ? {}", isRequestedUserInfoClaimsPresent);
        //Claims request parameter is allowed, only if 'openid' is part of the scope request parameter
        if(isRequestedUserInfoClaimsPresent && !Arrays.stream(requestedScopes).anyMatch(s  -> SCOPE_OPENID.equals(s)))
            throw new EsignetException(ErrorConstants.INVALID_SCOPE);

        log.info("Started to resolve claims based on the request scope {} and claims {}", requestedScopes, requestedClaims);

        Map<String, List<Map<String, Object>>> verifiedClaimsMap = new HashMap<>();
        if(isRequestedUserInfoClaimsPresent && requestedClaims.getUserinfo().get(VERIFIED_CLAIMS) != null) {
            JsonNode verifiedClaims = requestedClaims.getUserinfo().get(VERIFIED_CLAIMS);
            if(verifiedClaims.isArray()) {
                Iterator itr = verifiedClaims.iterator();
                while(itr.hasNext()) {
                    resolveVerifiedClaims((JsonNode) itr.next(), verifiedClaimsMap);
                }
            }
            else {
                resolveVerifiedClaims(verifiedClaims, verifiedClaimsMap);
            }
        }

        //get claims based on scope
        List<String> claimBasedOnScope = new ArrayList<>();
        Arrays.stream(requestedScopes)
                .forEach(scope -> { claimBasedOnScope.addAll(claims.getOrDefault(scope, new ArrayList<>())); });

        log.info("Resolved claims: {} based on request scope : {}", claimBasedOnScope, requestedScopes);

        //claims considered only if part of registered claims
        if(clientDetailDto.getClaims() != null) {
            clientDetailDto.getClaims()
                    .stream()
                    .forEach( claimName -> {
                        if(isRequestedUserInfoClaimsPresent && requestedClaims.getUserinfo().containsKey(claimName))
                            addClaimDetail(resolvedClaims, claimName, convertJsonNodeToClaimDetail(requestedClaims.getUserinfo().get(claimName)));
                        else if(claimBasedOnScope.contains(claimName))
                            addClaimDetail(resolvedClaims, claimName, null);

                        //Verified claim request takes priority
                        if(verifiedClaimsMap.containsKey(claimName))
                            resolvedClaims.getUserinfo().put(claimName, verifiedClaimsMap.get(claimName));
                    });
        }

        log.info("Final resolved user claims : {}", resolvedClaims);
        return resolvedClaims;
    }

    protected boolean isVerifiedClaimRequested(OIDCTransaction transaction) {
        return transaction.getResolvedClaims().getUserinfo() != null &&
                transaction.getResolvedClaims().getUserinfo()
                        .entrySet()
                        .stream()
                        .anyMatch( entry -> entry.getValue().stream().anyMatch( m-> m.get("verification") != null));
    }

    /**
     * This method is used to validate the requested claims against the accepted claims
     * <ul>
     *     <li>Checks Performed</li>
     *     <ul>
     *         <li>accepted Claims should be subset of requested claims</li>
     *         <li>essential Claims should be a subset of accepted claims</li>
     *     </ul>
     * </ul>
     *
     * @param transaction object containg OIDC transaction details
     * @param acceptedClaims list of accepted claims
     * @throws EsignetException
     *
     */
    protected void validateAcceptedClaims(OIDCTransaction transaction, List<String> acceptedClaims) throws EsignetException {
        Map<String, List<Map<String,Object>>> userinfo = Optional.ofNullable(transaction.getResolvedClaims())
                .map(Claims::getUserinfo)
                .orElse(Collections.emptyMap());

        List<String> essentialClaims = transaction.getEssentialClaims() == null ? Collections.emptyList() : transaction.getEssentialClaims();

        Set<String> allRequestedClaims = userinfo.keySet();
        Set<String> acceptedClaimsSet = new HashSet<>(Optional.ofNullable(acceptedClaims).orElse(Collections.emptyList()));

        if (essentialClaims.stream().anyMatch(c -> !acceptedClaimsSet.contains(c))
                || !allRequestedClaims.containsAll(acceptedClaimsSet)) {
            throw new EsignetException(INVALID_ACCEPTED_CLAIM);
        }
    }


    protected ClaimStatus getClaimStatus(String claim, List<Map<String, Object>> claimDetails, Map<String,
            List<JsonNode>> storedVerificationMetadata) {
        if(storedVerificationMetadata == null || storedVerificationMetadata.isEmpty())
            return new ClaimStatus(claim, false, false);

        //if the claim is requested without any verification metadata
        if(CollectionUtils.isEmpty(claimDetails) || claimDetails.stream().allMatch( m -> m.get("verification") == null))
            return new ClaimStatus(claim, (storedVerificationMetadata.getOrDefault(claim, Collections.emptyList())!=null
                    && !storedVerificationMetadata.getOrDefault(claim, Collections.emptyList()).isEmpty()),
                    storedVerificationMetadata.containsKey(claim));

        log.info("Request to fetch verification metadata for {} with filter criteria : {}", claim, claimDetails);
        List<JsonNode> storedVerificationDetails = storedVerificationMetadata.get(claim);

        //if the claim key is present but the value is null or empty then it is considered to be available, and not verified
        if(storedVerificationDetails == null)
            return new ClaimStatus(claim, false, storedVerificationMetadata.containsKey(claim));

        List<Map<String, Object>> requestedVerifiedClaimDetails = claimDetails.stream()
                .filter(m -> m.get("verification") != null)
                .collect(Collectors.toList());

        boolean verified = storedVerificationDetails.stream()
                .anyMatch(vd -> applyFilterOnStoredVerificationMetadata(vd, requestedVerifiedClaimDetails));

        return new ClaimStatus(claim, verified, true);
    }

    //TODO Add matcher for assurance_process and evidence
    private boolean applyFilterOnStoredVerificationMetadata(JsonNode storedVerificationDetail,
                                                            List<Map<String, Object>> requestedClaimDetails) {
        return requestedClaimDetails.stream()
                .anyMatch( m -> filterCriteriaMatcher.doMatch((Map<String, Object>)m.get("verification"), "trust_framework", storedVerificationDetail) &&
                        filterCriteriaMatcher.doMatch((Map<String, Object>)m.get("verification"), "time", storedVerificationDetail) &&
                        filterCriteriaMatcher.doMatch((Map<String, Object>)m.get("verification"), "verification_process", storedVerificationDetail) &&
                        filterCriteriaMatcher.doMatch((Map<String, Object>)m.get("verification"), "assurance_level", storedVerificationDetail));
                        //filterCriteriaMatcher.doMatch((Map<String, Object>)m.get("verification"), storedVerificationDetail.getAssurance_process()) &&
                        //filterCriteriaMatcher.doMatch((Map<String, Object>)m.get("verification"), storedVerificationDetail.getEvidence()) );
    }


    //We cannot use DTO, "data minimization" is one of the important requirement of https://openid.net/specs/openid-connect-4-identity-assurance-1_0.html
    //Only the requested verification metadata should be given to RP.
    //If we use DTO, there is no way to differentiate between what is requested and which metadata is set to null during deserialization.
    //Hence, using JsonNode to store the resolved claim detail.
    private void resolveVerifiedClaims(JsonNode verifiedClaims, Map<String, List<Map<String, Object>>> verifiedClaimsMap) {
        Map verifiedClaim = convertJsonNodeToClaimDetail(verifiedClaims);
        validateVerifiedClaims(verifiedClaim);
        //iterate through all the claims in the verified_claims object
        Map<String, Object> claimDetailMap = (Map<String, Object>)verifiedClaim.get("claims");
        for(Map.Entry<String, Object> entry : claimDetailMap.entrySet()) {
            Map<String, Object> claimDetail = new HashMap<>();
            claimDetail.put("verification", verifiedClaim.get("verification"));

            if(entry.getValue() != null) {
                Map<String, Object> value = (Map<String, Object>)entry.getValue();
                claimDetail.put("essential", value.getOrDefault("essential", false));
                claimDetail.put("value", value.get("value"));
                claimDetail.put("values", value.get("values"));
                claimDetail.put("purpose", value.get("purpose"));
            }

            if(!verifiedClaimsMap.containsKey(entry.getKey())) {
                verifiedClaimsMap.put(entry.getKey(), new ArrayList<>());
            }
            verifiedClaimsMap.get(entry.getKey()).add(claimDetail);
        }
    }

    private Map convertJsonNodeToClaimDetail(JsonNode claimDetailJsonNode) {
        try {
            if(claimDetailJsonNode.isNull())
                return null;
            return objectMapper.treeToValue(claimDetailJsonNode, Map.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse the requested claim details", e);
        }
        throw new EsignetException(ErrorConstants.INVALID_CLAIM);
    }

    //TODO replace this with schema validation
    private void validateVerifiedClaims(Map verifiedClaim) {
        if(verifiedClaim == null)
            throw new EsignetException(ErrorConstants.INVALID_VERIFIED_CLAIMS);

        if(verifiedClaim.get("verification") == null || ((Map)verifiedClaim.get("verification")).isEmpty())
            throw new EsignetException(ErrorConstants.INVALID_VERIFICATION);

        if(verifiedClaim.get("claims") == null || ((Map)verifiedClaim.get("claims")).isEmpty())
            throw new EsignetException(ErrorConstants.INVALID_VERIFIED_CLAIMS);
    }

    private void addClaimDetail(Claims resolvedClaims, String claim, Map claimDetail) {
        if(!resolvedClaims.getUserinfo().containsKey(claim)) {
            resolvedClaims.getUserinfo().put(claim, new ArrayList<>());
        }
        if(claimDetail != null)
            resolvedClaims.getUserinfo().get(claim).add(claimDetail);
    }
}
