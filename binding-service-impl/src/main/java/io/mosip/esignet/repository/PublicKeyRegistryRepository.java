/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import io.mosip.esignet.entity.PublicKeyRegistry;
import io.mosip.esignet.entity.RegistryId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface PublicKeyRegistryRepository extends JpaRepository<PublicKeyRegistry, RegistryId> {

	List<PublicKeyRegistry> findByIdHashAndAuthFactorInAndExpiredtimesGreaterThan(String idHash, Set<String> authFactor, LocalDateTime currentDateTime);

	Optional<PublicKeyRegistry> findFirstByPsuTokenAndAuthFactorOrderByExpiredtimesDesc(String psuToken, String authFactor);

    Optional<PublicKeyRegistry> findOptionalByPublicKeyHashAndPsuTokenNot(String publicKeyHash, String psuToken);

	@Modifying
	@Query(value = "UPDATE public_key_registry set public_key= :publicKey , public_key_hash= :publicKeyHash , expire_dtimes= :expireDTimes, " +
            "certificate= :certificate where psu_token= :psuToken and auth_factor= :authFactor", nativeQuery = true)
	int updatePublicKeyRegistry(String publicKey, String publicKeyHash, LocalDateTime expireDTimes, String psuToken, String certificate, String authFactor);

	Optional<PublicKeyRegistry>findFirstByIdHashAndThumbprintAndExpiredtimesGreaterThanOrderByExpiredtimesDesc(String idHash, String thumbPrint, LocalDateTime currentDate);
}
