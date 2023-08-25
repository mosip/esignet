package io.mosip.esignet.vci.filter;

import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.KeySourceException;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.JWTClaimsSetAwareJWSKeySelector;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.List;

@Component
public class IssuerBasedKeySelector implements JWTClaimsSetAwareJWSKeySelector {

    @Override
    public List<? extends Key> selectKeys(JWSHeader header, JWTClaimsSet claimsSet, SecurityContext context)
            throws KeySourceException {
        String issuer = claimsSet.getIssuer();
        //Get issuer based keys
        return null;
    }
}
