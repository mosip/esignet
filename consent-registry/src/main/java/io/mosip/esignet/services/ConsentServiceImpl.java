package io.mosip.esignet.services;


import io.mosip.esignet.api.spi.AuditPlugin;
import io.mosip.esignet.api.util.Action;
import io.mosip.esignet.api.util.ActionStatus;
import io.mosip.esignet.core.dto.Consent;
import io.mosip.esignet.core.dto.ConsentRequest;
import io.mosip.esignet.core.dto.UserConsentRequest;
import io.mosip.esignet.core.spi.ConsentService;
import io.mosip.esignet.core.util.AuditHelper;
import io.mosip.esignet.mapper.ConsentMapper;
import io.mosip.esignet.repository.ConsentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Slf4j
public class ConsentServiceImpl implements ConsentService {

    private  final ConsentRepository consentRepository;

    private final AuditPlugin auditWrapper;

    @Value("${mosip.esignet.audit.claim-name:preferred_username}")
    private String claimName;

    public ConsentServiceImpl(ConsentRepository consentRepository, AuditPlugin auditWrapper) {
        this.consentRepository = consentRepository;
        this.auditWrapper = auditWrapper;
    }

    @Override
    public Optional<Consent> getUserConsent(UserConsentRequest userConsentRequest) {

        Optional<io.mosip.esignet.entity.Consent> consentOptional = consentRepository.
                findFirstByClientIdAndPsuValueOrderByCreatedOnDesc(userConsentRequest.getClientId(),
                        userConsentRequest.getPsu_token());

        auditWrapper.logAudit(AuditHelper.getClaimValue(SecurityContextHolder.getContext(), claimName),
                Action.OIDC_CLIENT_UPDATE, ActionStatus.SUCCESS,
                AuditHelper.buildAuditDto(userConsentRequest.getClientId()), null);


        if (consentOptional.isPresent()) {
            Consent consentDto = ConsentMapper.toDto( consentOptional.get());

            return Optional.of(consentDto);
        }
        return Optional.empty();
    }

    @Override
    public Consent saveUserConsent(ConsentRequest consentRequest) {
        //convert ConsentRequest to Entity
        io.mosip.esignet.entity.Consent consent =ConsentMapper.toEntiyt(consentRequest);

        auditWrapper.logAudit(AuditHelper.getClaimValue(SecurityContextHolder.getContext(), claimName),
                Action.OIDC_CLIENT_UPDATE, ActionStatus.SUCCESS,
                AuditHelper.buildAuditDto(consentRequest.getClientId()), null);


        //Entity to DTO conversion and save
        Consent consentDto =ConsentMapper.toDto(consentRepository.save(consent));
        return consentDto;
    }
}
