/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.services;

import io.mosip.esignet.core.dto.PublicKeyRegistry;
import io.mosip.esignet.core.spi.PublicKeyRegistryService;
import io.mosip.esignet.repository.PublicKeyRegistryRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Slf4j
@Service
public class PublicKeyRegistryServiceImpl implements PublicKeyRegistryService {

    @Autowired
    private PublicKeyRegistryRepository publicKeyRegistryRepository;

    @Override
    public Optional<PublicKeyRegistry> findLatestPublicKeyByPsuTokenAndAuthFactor(String psuToken, String authFactor) {
        Optional<io.mosip.esignet.entity.PublicKeyRegistry> optionalPublicKeyRegistry = publicKeyRegistryRepository.findLatestByPsuTokenAndAuthFactor(psuToken,authFactor);
        if(optionalPublicKeyRegistry.isPresent()) {
            PublicKeyRegistry publicKeyRegistry = new PublicKeyRegistry();
            publicKeyRegistry.setPublicKey(optionalPublicKeyRegistry.get().getPublicKey());
            publicKeyRegistry.setPsuToken(optionalPublicKeyRegistry.get().getPsuToken());
            publicKeyRegistry.setAuthFactor(optionalPublicKeyRegistry.get().getAuthFactor());
            return Optional.of(publicKeyRegistry);
        }
        return Optional.empty();
    }

    @Override
    public Optional<PublicKeyRegistry> findFirstByIdHashAndThumbprintAndExpiredtimes(String idHash, String thumbPrint) {
        Optional<io.mosip.esignet.entity.PublicKeyRegistry> optionalPublicKeyRegistry=publicKeyRegistryRepository
                .findFirstByIdHashAndThumbprintAndExpiredtimesGreaterThanOrderByExpiredtimesDesc(idHash,thumbPrint,LocalDateTime.now(ZoneOffset.UTC));
        if(optionalPublicKeyRegistry.isPresent()) {
            PublicKeyRegistry publicKeyRegistry = new PublicKeyRegistry();
            publicKeyRegistry.setPublicKey(optionalPublicKeyRegistry.get().getPublicKey());
            publicKeyRegistry.setPsuToken(optionalPublicKeyRegistry.get().getPsuToken());
            publicKeyRegistry.setAuthFactor(optionalPublicKeyRegistry.get().getAuthFactor());
            publicKeyRegistry.setCertificate(optionalPublicKeyRegistry.get().getCertificate());
            return Optional.of(publicKeyRegistry);
        }
        return Optional.empty();

    }
}
