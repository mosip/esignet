/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.services;


import io.mosip.esignet.api.spi.AuditPlugin;
import io.mosip.esignet.api.util.Action;
import io.mosip.esignet.api.util.ActionStatus;
import io.mosip.esignet.core.dto.ConsentDetail;
import io.mosip.esignet.core.dto.UserConsent;
import io.mosip.esignet.core.dto.UserConsentRequest;
import io.mosip.esignet.core.spi.ConsentService;
import io.mosip.esignet.core.util.AuditHelper;
import io.mosip.esignet.entity.ConsentHistory;
import io.mosip.esignet.mapper.ConsentMapper;
import io.mosip.esignet.repository.ConsentHistoryRepository;
import io.mosip.esignet.repository.ConsentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Service
@Slf4j
public class ConsentServiceImpl implements ConsentService {

    @Autowired
    private  ConsentRepository consentRepository;

    @Autowired
    private ConsentHistoryRepository consentHistoryRepository;

    @Autowired
    private AuditPlugin auditWrapper;

    @Value("${mosip.esignet.audit.claim-name:preferred_username}")
    private String claimName;

    @Override
    public Optional<ConsentDetail> getUserConsent(UserConsentRequest userConsentRequest) {

        Optional<io.mosip.esignet.entity.ConsentDetail> consentOptional = consentRepository.
                findByClientIdAndPsuToken(userConsentRequest.getClientId(),
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
    @Transactional
    public ConsentDetail saveUserConsent(UserConsent userConsent) {
        Optional<io.mosip.esignet.entity.ConsentDetail> clientDetailOptional =
                consentRepository.findByClientIdAndPsuToken(userConsent.getClientId(), userConsent.getPsuToken());
        if(clientDetailOptional.isPresent()) {
            consentRepository.deleteByClientIdAndPsuToken(userConsent.getClientId(), userConsent.getPsuToken());
            consentRepository.flush();
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        //convert ConsentRequest to Entity
        ConsentHistory consentHistory = ConsentMapper.toConsentHistoryEntity(userConsent);
        consentHistory.setCreatedtimes(now);
        consentHistoryRepository.save(consentHistory);

        io.mosip.esignet.entity.ConsentDetail consentDetail =ConsentMapper.toEntity(userConsent);
        consentDetail.setCreatedtimes(now);

        ConsentDetail consentDetailDto =ConsentMapper.toDto(consentRepository.save(consentDetail));
        auditWrapper.logAudit(AuditHelper.getClaimValue(SecurityContextHolder.getContext(), claimName),
                Action.SAVE_USER_CONSENT, ActionStatus.SUCCESS,
                AuditHelper.buildAuditDto(userConsent.getClientId()), null);
        return consentDetailDto;
    }

    @Override
    @Transactional
    public void deleteUserConsent(String clientId, String psuToken) {
        consentRepository.deleteByClientIdAndPsuToken(clientId, psuToken);
    }
}
