package io.mosip.esignet.services;


import io.mosip.esignet.api.spi.AuditPlugin;
import io.mosip.esignet.api.util.Action;
import io.mosip.esignet.api.util.ActionStatus;
import io.mosip.esignet.core.dto.ConsentDetail;
import io.mosip.esignet.core.dto.UserConsent;
import io.mosip.esignet.core.dto.UserConsentRequest;
import io.mosip.esignet.core.spi.ConsentService;
import io.mosip.esignet.core.util.AuditHelper;
import io.mosip.esignet.mapper.ConsentMapper;
import io.mosip.esignet.repository.ConsentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Slf4j
public class ConsentServiceImpl implements ConsentService {

    @Autowired
    private  ConsentRepository consentRepository;

    @Autowired
    private AuditPlugin auditWrapper;

    @Value("${mosip.esignet.audit.claim-name:preferred_username}")
    private String claimName;

    @Override
    public Optional<ConsentDetail> getUserConsent(UserConsentRequest userConsentRequest) {

        Optional<io.mosip.esignet.entity.ConsentDetail> consentOptional = consentRepository.
                findFirstByClientIdAndPsuTokenOrderByCreatedtimesDesc(userConsentRequest.getClientId(),
                        userConsentRequest.getPsuToken());
        if (consentOptional.isPresent()) {
            ConsentDetail consentDetailDto = ConsentMapper.toDto( consentOptional.get());

            return Optional.of(consentDetailDto);
        }
        auditWrapper.logAudit(AuditHelper.getClaimValue(SecurityContextHolder.getContext(), claimName),
                Action.GET_USER_CONSENT, ActionStatus.SUCCESS,
                AuditHelper.buildAuditDto(userConsentRequest.getClientId()), null);
        return Optional.empty();
    }

    @Override
    public ConsentDetail saveUserConsent(UserConsent userConsent) {
        //convert ConsentRequest to Entity
        io.mosip.esignet.entity.ConsentDetail consentDetail =ConsentMapper.toEntity(userConsent);
        consentDetail.setCreatedtimes(LocalDateTime.now());
        ConsentDetail consentDetailDto =ConsentMapper.toDto(consentRepository.save(consentDetail));
        auditWrapper.logAudit(AuditHelper.getClaimValue(SecurityContextHolder.getContext(), claimName),
                Action.SAVE_USER_CONSENT, ActionStatus.SUCCESS,
                AuditHelper.buildAuditDto(userConsent.getClientId()), null);
        return consentDetailDto;
    }
}
