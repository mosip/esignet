package io.mosip.esignet.household.integration.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itextpdf.tool.xml.exceptions.NotImplementedException;
import io.mosip.esignet.api.dto.*;
import io.mosip.esignet.api.exception.KycAuthException;
import io.mosip.esignet.api.exception.KycExchangeException;
import io.mosip.esignet.api.exception.KycSigningCertificateException;
import io.mosip.esignet.api.exception.SendOtpException;
import io.mosip.esignet.api.spi.Authenticator;
import io.mosip.esignet.household.integration.dto.KycTransactionDto;
import io.mosip.esignet.household.integration.entity.HouseholdView;
import io.mosip.esignet.household.integration.repository.HouseholdViewRepository;
import io.mosip.esignet.household.integration.util.HelperUtil;
import io.mosip.kernel.keymanagerservice.dto.AllCertificatesDataResponseDto;
import io.mosip.kernel.keymanagerservice.dto.CertificateDataResponseDto;
import io.mosip.kernel.keymanagerservice.service.KeymanagerService;
import io.mosip.kernel.signature.dto.JWTSignatureRequestDto;
import io.mosip.kernel.signature.dto.JWTSignatureResponseDto;
import io.mosip.kernel.signature.service.SignatureService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.*;

import static io.mosip.esignet.household.integration.util.ErrorConstants.*;

@Component
@Slf4j
public class HouseholdAuthenticator implements Authenticator {

    private static final String     TOKEN_FORMAT = "%s%s";
    public static final String APPLICATION_ID = "HH_AUTHENTICATION_SERVICE";

    @Autowired
    HouseholdAuthenticatorHelper householdAuthenticatorHelper;

    @Autowired
    private HouseholdViewRepository householdViewRepository;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    SignatureService signatureService;

    @Autowired
    KeymanagerService keymanagerService;


    @Override
    public KycAuthResult doKycAuth(String relyingPartyId, String clientId, KycAuthDto kycAuthDto) throws KycAuthException {
        log.info("Started  kyc-auth request with transactionId : {} && clientId : {}",
                kycAuthDto.getTransactionId(), clientId);
        if(CollectionUtils.isEmpty(kycAuthDto.getChallengeList())){
            throw new KycAuthException(INVALID_AUTH_CHALLENGE);
        }
        Optional<HouseholdView> houseHoldViewOptional = householdViewRepository.findByIdNumber(kycAuthDto.getIndividualId());;
        if(!houseHoldViewOptional.isPresent()){
            log.error("No household found with individualId : {}",kycAuthDto.getIndividualId());
            throw new KycAuthException(INVALID_INDIVIDUAL_ID);
        }
        HouseholdView householdView = houseHoldViewOptional.get();

        for(AuthChallenge authChallenge:kycAuthDto.getChallengeList())
        {
            householdAuthenticatorHelper.validateAuthChallenge(householdView,authChallenge);
        }
        log.info("successfully validated all the auth challenges");

        String kycToken = HelperUtil.generateB64EncodedHash(HelperUtil.ALGO_SHA3_256,
                String.format(TOKEN_FORMAT, kycAuthDto.getTransactionId(), householdView.getHouseholdId()));
        String   psut = HelperUtil.generateB64EncodedHash(HelperUtil.ALGO_SHA3_256,
                String.format(TOKEN_FORMAT, relyingPartyId, householdView.getHouseholdId()));

        KycTransactionDto kycTransactionDto = householdAuthenticatorHelper.setKycAuthTransaction(kycToken, psut, householdView.getHouseholdId());
        KycAuthResult kycAuthResult=new KycAuthResult();
        kycAuthResult.setKycToken(kycTransactionDto.getKycToken());
        kycAuthResult.setPartnerSpecificUserToken(kycTransactionDto.getPsut());
        return kycAuthResult;
    }

    @Override
    public KycExchangeResult doKycExchange(String relyingPartyId, String clientId, KycExchangeDto kycExchangeDto) throws KycExchangeException {
        KycTransactionDto kycAuthTransactionDto = householdAuthenticatorHelper.getKycAuthTransaction(kycExchangeDto.getKycToken());
        if (kycAuthTransactionDto == null) {
            throw new KycExchangeException(INVALID_TRANSACTION);
        }

        Map<String, String> kycMap = new HashMap<>();
        kycMap.put("sub", String.valueOf(kycAuthTransactionDto.getHouseholdId()));

        try {
            String payload = objectMapper.writeValueAsString(kycMap);
            JWTSignatureRequestDto jwtSignatureRequestDto = new JWTSignatureRequestDto();
            jwtSignatureRequestDto.setApplicationId(APPLICATION_ID);
            jwtSignatureRequestDto.setReferenceId("");
            jwtSignatureRequestDto.setIncludePayload(true);
            jwtSignatureRequestDto.setIncludeCertificate(false);
            jwtSignatureRequestDto.setDataToSign(HelperUtil.b64Encode(payload));
            jwtSignatureRequestDto.setIncludeCertHash(false);
            JWTSignatureResponseDto responseDto = signatureService.jwtSign(jwtSignatureRequestDto);

            KycExchangeResult kycExchangeResult = new KycExchangeResult();
            kycExchangeResult.setEncryptedKyc(responseDto.getJwtSignedData());
            return kycExchangeResult;
        } catch (Exception e) {
            log.error("Error while signing the payload", e);
        }
        throw new KycExchangeException(KYC_EXCHANGE_FAILED);
    }

    @Override
    public SendOtpResult sendOtp(String relyingPartyId, String clientId, SendOtpDto sendOtpDto) throws SendOtpException {
        throw new NotImplementedException();
    }

    @Override
    public boolean isSupportedOtpChannel(String channel) {
        return false;
    }

    @Override
    public List<KycSigningCertificateData> getAllKycSigningCertificates() throws KycSigningCertificateException {
        List<KycSigningCertificateData> certs = new ArrayList<>();
        AllCertificatesDataResponseDto allCertificatesDataResponseDto = keymanagerService.getAllCertificates(APPLICATION_ID,
                Optional.empty());
        for (CertificateDataResponseDto dto : allCertificatesDataResponseDto.getAllCertificates()) {
            certs.add(new KycSigningCertificateData(dto.getKeyId(), dto.getCertificateData(),
                    dto.getExpiryAt(), dto.getIssuedAt()));
        }
        return certs;
    }
}
