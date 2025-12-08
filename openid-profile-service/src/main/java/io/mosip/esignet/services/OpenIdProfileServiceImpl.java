/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.services;

import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.entity.OpenIdProfile;
import io.mosip.esignet.repository.OpenIdProfileRepository;
import io.mosip.esignet.core.spi.OpenIdProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class OpenIdProfileServiceImpl implements OpenIdProfileService {

    private static final String FEATURES = "features";

    @Autowired
    OpenIdProfileRepository openIdProfileRepository;

    /**
     * Get the features associated with the profile
     * @param profileName name of the profile - fapi2.0. nisdsp, gov, none etc
     * @return list of features associated with the profile
     */
    @Override
    @Cacheable(value = FEATURES, key = "'latest_features'")
    public List<String> getFeaturesByProfileName(String profileName) {
        List<OpenIdProfile> profiles = openIdProfileRepository.findByProfileName(profileName);
        if (profiles == null || profiles.isEmpty()) {
            throw new EsignetException("No features found for openid profile: " + profileName);
        }
        return profiles
                .stream()
                .map(OpenIdProfile::getFeature)
                .collect(Collectors.toList());
    }
}
