package io.mosip.esignet.vci.services;

import foundation.identity.jsonld.JsonLDObject;
import io.mosip.esignet.api.dto.VCRequestDto;
import io.mosip.esignet.api.dto.VCResult;
import io.mosip.esignet.api.spi.VCIssuancePlugin;
import io.mosip.esignet.core.constants.Constants;
import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.dto.vci.CredentialMetadata;
import io.mosip.esignet.core.dto.vci.CredentialRequest;
import io.mosip.esignet.core.dto.vci.CredentialResponse;
import io.mosip.esignet.core.dto.vci.ParsedAccessToken;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.core.exception.InvalidRequestException;
import io.mosip.esignet.core.exception.NotAuthenticatedException;
import io.mosip.esignet.core.spi.VCIssuanceService;
import io.mosip.esignet.vci.pop.ProofValidator;
import io.mosip.esignet.vci.pop.ProofValidatorFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.beanutils.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.lang.reflect.InvocationTargetException;
import java.util.*;

@Slf4j
@Service
public class VCIssuanceServiceImpl implements VCIssuanceService {

    @Value("#{${mosip.esignet.vci.key-values}}")
    private Map<String, Object> issuerMetadata;

    @Autowired
    private VCIUtilService vciUtilService;

    @Autowired
    private ParsedAccessToken parsedAccessToken;

    @Autowired
    private VCIssuancePlugin vcIssuancePlugin;

    @Autowired
    private ProofValidatorFactory proofValidatorFactory;

    private List<LinkedHashMap<String, Object>> supportedCredentials;


    @Override
    public CredentialResponse getCredential(CredentialRequest credentialRequest) {
        if(!parsedAccessToken.isActive())
            throw new NotAuthenticatedException();

        String scopeClaim = (String) parsedAccessToken.getClaims().getOrDefault("scope", "");
        CredentialMetadata credentialMetadata = null;
        for(String scope : scopeClaim.split(Constants.SPACE)) {
            Optional<CredentialMetadata> result = getScopeCredentialMapping(scope);
            if(result.isPresent()) {
                credentialMetadata = result.get(); //considering only first credential scope
                break;
            }
        }

        if(credentialMetadata == null) {
            log.error("No credential mapping found for the provided scope {}", scopeClaim);
            throw new EsignetException(ErrorConstants.INVALID_SCOPE);
        }

        ProofValidator proofValidator = proofValidatorFactory.getProofValidator(credentialRequest.getProof().getProof_type());
        if(!proofValidator.validate(credentialRequest.getProof()))
            throw new EsignetException(ErrorConstants.PROOF_OF_POSSESSION_FAILED);
        String holderId = proofValidator.getKeyMaterial(credentialRequest.getProof());

        //Get VC from configured plugin implementation
        VCResult<?> vcResult = getVerifiableCredential(credentialRequest, credentialMetadata, holderId);
        if(vcResult == null || vcResult.getCredential() == null) {
            log.error("Failed to generate VC : {}", vcResult);
            throw new EsignetException(ErrorConstants.VC_ISSUANCE_FAILED);
        }
        return getCredentialResponse(credentialRequest.getFormat(), vcResult);
    }

    @Override
    public Map<String, Object> getCredentialIssuerMetadata() {
        return issuerMetadata;
    }

    private VCResult<?> getVerifiableCredential(CredentialRequest credentialRequest, CredentialMetadata credentialMetadata,
                                                String holderId) {
        VCRequestDto vcRequestDto = new VCRequestDto();
        switch (credentialRequest.getFormat()) {
            case "ldp_vc" :
                validateLdpVcFormatRequest(credentialRequest, credentialMetadata);
                vcRequestDto.setFormat(credentialRequest.getFormat());
                vcRequestDto.setTypes(credentialRequest.getCredential_definition().getTypes().toArray(new String[0]));
                vcRequestDto.setCredentialSubject(credentialRequest.getCredential_definition().getCredentialSubject());
                return vcIssuancePlugin.getVerifiableCredentialWithLinkedDataProof(vcRequestDto, holderId,
                        parsedAccessToken.getClaims());

            // jwt_vc_json & jwt_vc_json-ld cases are merged
            case "jwt_vc_json-ld" :
                vcRequestDto.setTypes(credentialRequest.getTypes().toArray(new String[0]));
                vcRequestDto.setCredentialSubject(credentialRequest.getCredentialSubject());
            case "jwt_vc_json" :
                vcRequestDto.setFormat(credentialRequest.getFormat());
                return vcIssuancePlugin.getVerifiableCredential(vcRequestDto, holderId,
                        parsedAccessToken.getClaims());
            default:
                throw new EsignetException(ErrorConstants.UNSUPPORTED_VC_FORMAT);
        }
    }

    private CredentialResponse<?> getCredentialResponse(String format, VCResult<?> vcResult) {
        switch (format) {
            case "ldp_vc":
                CredentialResponse<JsonLDObject> ldpVcResponse = new CredentialResponse<>();
                ldpVcResponse.setCredential((JsonLDObject)vcResult.getCredential());
                ldpVcResponse.setFormat(vcResult.getFormat());
                return ldpVcResponse;

            case "jwt_vc_json-ld":
            case "jwt_vc_json":
                CredentialResponse<String> jsonResponse = new CredentialResponse<>();
                jsonResponse.setCredential((String)vcResult.getCredential());
                jsonResponse.setFormat(vcResult.getFormat());
                return jsonResponse;
        }
        throw new EsignetException(ErrorConstants.UNSUPPORTED_VC_FORMAT);
    }

    private Optional<CredentialMetadata>  getScopeCredentialMapping(String scope) {
        if(supportedCredentials == null) {
            supportedCredentials = (List<LinkedHashMap<String, Object>>) issuerMetadata.get("credentials_supported");
        }
        Optional<LinkedHashMap<String, Object>> result = supportedCredentials.stream()
                .filter(cm -> cm.get("scope").equals(scope)).findFirst();
        if(result.isPresent()){
            CredentialMetadata credentialMetadata = new CredentialMetadata();
            try {
                BeanUtils.populate(credentialMetadata, result.get());
                return Optional.of(credentialMetadata);
            } catch (IllegalAccessException | InvocationTargetException e) {
                log.error("Failed to create credentialMetadata from configured map", e);
            }
        }
        return Optional.empty();
    }

    private void validateLdpVcFormatRequest(CredentialRequest credentialRequest,
                                               CredentialMetadata credentialMetadata) {
        if(Objects.isNull(credentialRequest.getCredential_definition()))
            throw new InvalidRequestException(ErrorConstants.INVALID_REQUEST);

        if(Objects.isNull(credentialRequest.getCredential_definition().getTypes()))
            throw new InvalidRequestException(ErrorConstants.INVALID_REQUEST);

        if(!(credentialRequest.getCredential_definition().getTypes().contains("VerifiableCredential") &&
        credentialRequest.getCredential_definition().getTypes().contains(credentialMetadata.getId())))
            throw new InvalidRequestException(ErrorConstants.UNSUPPORTED_VC_TYPE);

        //TODO need to validate Credential_definition as JsonLD document, if invalid throw exception
    }
}