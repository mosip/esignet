/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.binding.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import io.mosip.idp.binding.entity.PublicKeyRegistry;

public interface PublicKeyRegistryRepository extends JpaRepository<PublicKeyRegistry, String> {
	
	/**
	 * Query to fetch PublicKeyRegistry based on idHash
	 * 
	 * @param psuToken
	 * @return
	 */
	Optional<PublicKeyRegistry> findByIdHash(String idHash);

    @Query("SELECT pkr FROM PublicKeyRegistry pkr WHERE pkr.psuToken= :psuToken")
    Optional<PublicKeyRegistry> findByPsuToken(String psuToken);

    @Query("SELECT pkr FROM PublicKeyRegistry pkr WHERE pkr.publicKeyHash= :publicKeyHash and pkr.psuToken!= :psuToken")
    Optional<PublicKeyRegistry> findByPublicKeyHashNotEqualToPsuToken(String publicKeyHash, String psuToken);
}
