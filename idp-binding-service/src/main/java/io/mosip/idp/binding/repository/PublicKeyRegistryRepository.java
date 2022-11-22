package io.mosip.idp.binding.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import io.mosip.idp.binding.entity.PublicKeyRegistry;

public interface PublicKeyRegistryRepository extends JpaRepository<PublicKeyRegistry, String> {
	
	/**
     * Query to fetch PublicKeyRegistry based on individualId
     * @param individualId
     * @return
     */
	Optional<PublicKeyRegistry> findByIndividualId(String individualId);
	
	/**
     * Query to fetch PublicKeyRegistry with individualId which is not expired
     * @param individualId
     * @param currentDate
     * @return
     */
	PublicKeyRegistry findByIndividualIdAndExpiresOnGreaterThan(String individualId, LocalDateTime currentDate);

}
