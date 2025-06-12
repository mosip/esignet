package in.gov.uidai.services;

import in.gov.uidai.entities.MockIdentity;
import in.gov.uidai.repositories.IdentityRepository;
import io.mosip.esignet.api.dto.*;
import io.mosip.esignet.api.exception.KycAuthException;
import io.mosip.esignet.api.exception.KycExchangeException;
import io.mosip.esignet.api.exception.KycSigningCertificateException;
import io.mosip.esignet.api.exception.SendOtpException;
import io.mosip.esignet.api.spi.Authenticator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;


@Component
public class AuthenticatorPluginImpl implements Authenticator {

    @Autowired
    private IdentityRepository identityRepository;

    @Override
    public KycAuthResult doKycAuth(String relyingPartyId, String clientId, KycAuthDto kycAuthDto) throws KycAuthException {
        Optional<MockIdentity> mockIdentity = identityRepository.findById(kycAuthDto.getIndividualId());
        if (mockIdentity.isEmpty()) {
            throw new KycAuthException("INVALID_INDIVIDUAL_ID");
        }
        KycAuthResult kycAuthResult = new KycAuthResult();
        kycAuthResult.setKycToken("kycToken");
        kycAuthResult.setPartnerSpecificUserToken("partnersepcifictoken");
        return kycAuthResult;
    }

    @Override
    public KycExchangeResult doKycExchange(String relyingPartyId, String clientId, KycExchangeDto kycExchangeDto) throws KycExchangeException {
        KycExchangeResult kycExchangeResult = new KycExchangeResult();
        kycExchangeResult.setEncryptedKyc("kyc");
        return kycExchangeResult;
    }

    @Override
    public SendOtpResult sendOtp(String relyingPartyId, String clientId, SendOtpDto sendOtpDto) throws SendOtpException {
        SendOtpResult sendOtpResult = new SendOtpResult();
        sendOtpResult.setMaskedEmail("test");
        sendOtpResult.setMaskedMobile("test");
        sendOtpResult.setTransactionId(sendOtpDto.getTransactionId());
        return sendOtpResult;
    }

    @Override
    public boolean isSupportedOtpChannel(String channel) {
        return true;
    }

    @Override
    public List<KycSigningCertificateData> getAllKycSigningCertificates() throws KycSigningCertificateException {
        return List.of();
    }
}
