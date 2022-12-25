/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.binding.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import io.mosip.idp.binding.entity.PublicKeyRegistry;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface PublicKeyRegistryRepository extends JpaRepository<PublicKeyRegistry, String> {
	
	/**
	 * Query to fetch PublicKeyRegistry based on idHash
	 * 
	 * @param idHash
	 * @return
	 */
	Optional<PublicKeyRegistry> findByIdHashAndExpiredtimesGreaterThan(String idHash, LocalDateTime currentDateTime);

    @Query(value = "SELECT * FROM public_key_registry WHERE psu_token= :psuToken ORDER BY expire_dtimes DESC LIMIT 1", nativeQuery = true)
	Optional<PublicKeyRegistry> findOneByPsuToken(String psuToken);

    @Query("SELECT pkr FROM PublicKeyRegistry pkr WHERE pkr.publicKeyHash= :publicKeyHash and pkr.psuToken!= :psuToken")
    Optional<PublicKeyRegistry> findByPublicKeyHashNotEqualToPsuToken(String publicKeyHash, String psuToken);

	@Modifying
	@Query("UPDATE PublicKeyRegistry  pkr set pkr.publicKey= :publicKey , pkr.publicKeyHash= :publicKeyHash , pkr.expiredtimes= :expireDTimes, " +
			"pkr.certificate= :certificate, pkr.authFactors= :authFactors where pkr.psuToken= :psuToken")
	int updatePublicKeyRegistry(String publicKey, String publicKeyHash, LocalDateTime expireDTimes, String psuToken,
								String certificate, String authFactors);
}
