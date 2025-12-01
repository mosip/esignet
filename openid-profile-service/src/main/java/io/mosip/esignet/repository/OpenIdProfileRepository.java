/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.repository;

import io.mosip.esignet.entity.OpenIdProfile;
import io.mosip.esignet.entity.OpenIdProfileId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OpenIdProfileRepository extends JpaRepository<OpenIdProfile, OpenIdProfileId> {
    List<OpenIdProfile> findByProfileName(String profileName);
}
