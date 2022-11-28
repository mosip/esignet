package io.mosip.idp.services;

import io.mosip.idp.core.dto.*;
import io.mosip.idp.core.util.Constants;
import io.mosip.idp.core.util.IdentityProviderUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class AuthorizationHelperService {

    @Value("#{${mosip.idp.openid.scope.claims}}")
    private Map<String, List<String>> claims;

    @Value("#{${mosip.idp.supported.authorize.scopes}}")
    private List<String> authorizeScopes;

    public Map<String, List> getClaimNames(Claims resolvedClaims) {
        List<String> essentialClaims = new ArrayList<>();
        List<String> voluntaryClaims = new ArrayList<>();
        for(Map.Entry<String, ClaimDetail> claim : resolvedClaims.getUserinfo().entrySet()) {
            if(claim.getValue() != null && claim.getValue().isEssential())
                essentialClaims.add(claim.getKey());
            else
                voluntaryClaims.add(claim.getKey());
        }
        Map<String, List> result = new HashMap<>();
        result.put("essential", essentialClaims);
        result.put("voluntary", voluntaryClaims);
        return result;
    }

    public List<String> getAuthorizeScopes(String requestedScopes) {
        String[] scopes = IdentityProviderUtil.splitAndTrimValue(requestedScopes, Constants.SPACE);
        return Arrays.stream(scopes)
                .filter( s -> authorizeScopes.contains(s) )
                .collect(Collectors.toList());
    }
}
