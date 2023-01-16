package io.mosip.idp.authwrapper.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.esignet.core.dto.*;
import io.mosip.esignet.core.exception.KycAuthException;
import io.mosip.esignet.core.util.IdentityProviderUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Arrays;
import java.util.List;

import static io.mosip.esignet.core.util.ErrorConstants.AUTH_FAILED;

@Component
@Slf4j
public class MockHelperService {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${mosip.idp.authn.wrapper.validate-binding-url}")
    private String validateBindingUrl;

    private static final List<String> keyBoundAuthFactorTypes = Arrays.asList("WLA");

    public void validateKeyBoundAuth(String transactionId, String individualId, List<AuthChallenge> challengeList)
            throws KycAuthException {
        RequestWrapper<ValidateBindingRequest> requestWrapper = new RequestWrapper<>();
        requestWrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
        ValidateBindingRequest validateBindingRequest = new ValidateBindingRequest();
        validateBindingRequest.setIndividualId(individualId);
        validateBindingRequest.setTransactionId(transactionId);
        validateBindingRequest.setChallenges(challengeList);
        requestWrapper.setRequest(validateBindingRequest);

        try {
            String requestBody = objectMapper.writeValueAsString(requestWrapper);
            RequestEntity requestEntity = RequestEntity
                    .post(UriComponentsBuilder.fromUriString(validateBindingUrl).build().toUri())
                    .contentType(MediaType.APPLICATION_JSON_UTF8)
                    .body(requestBody);
            ResponseEntity<ResponseWrapper<ValidateBindingResponse>> responseEntity = restTemplate.exchange(requestEntity,
                    new ParameterizedTypeReference<ResponseWrapper<ValidateBindingResponse>>() {});

            if(responseEntity.getStatusCode().is2xxSuccessful() && responseEntity.getBody() != null) {
                ResponseWrapper<ValidateBindingResponse> responseWrapper = responseEntity.getBody();
                if(responseWrapper.getResponse() != null && transactionId.equals(responseWrapper.getResponse().getTransactionId())) {
                    log.info("Key bound auth is successful : {}", validateBindingRequest.getChallenges().size());
                    return;
                }
                log.error("Error response received from binding-service : {} && Errors: {}", transactionId, responseWrapper.getErrors());
                throw new KycAuthException(CollectionUtils.isEmpty(responseWrapper.getErrors()) ?
                        AUTH_FAILED : responseWrapper.getErrors().get(0).getErrorCode());
            }
        } catch (KycAuthException e) { throw e; } catch (Exception e) {
            log.error("Failed validate binding tokens", e);
        }
        throw new KycAuthException(AUTH_FAILED);
    }
}
