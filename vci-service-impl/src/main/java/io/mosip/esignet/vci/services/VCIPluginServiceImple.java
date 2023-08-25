package io.mosip.esignet.vci.services;

import foundation.identity.jsonld.JsonLDObject;
import io.mosip.esignet.api.dto.VCRequestDto;
import io.mosip.esignet.api.dto.VCResult;
import io.mosip.esignet.api.spi.VCIssuancePlugin;
import org.apache.commons.lang3.NotImplementedException;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class VCIPluginServiceImple implements VCIssuancePlugin {

    @Override
    public VCResult<String> getVerifiableCredential(VCRequestDto vcRequestDto, String holderId, Map identityDetails) {
        throw new NotImplementedException();
    }

    @Override
    public VCResult<JsonLDObject> getVerifiableCredentialWithLinkedDataProof(VCRequestDto vcRequestDto, String holderId, Map<String, Object> identityDetails) {
        VCResult<JsonLDObject> vcResult = new VCResult<>();
        vcResult.setFormat("ldp_vc");
        vcResult.setCredential(new JsonLDObject());
        return vcResult;
    }
}
