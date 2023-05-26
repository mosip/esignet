package io.mosip.esignet.repository;

import io.mosip.esignet.entity.Consent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConsentRepository extends JpaRepository<Consent, UUID> {

      Optional<Consent> findFirstByClientIdAndPsuValueOrderByCreatedOnDesc(String clientId, String psuValue);
}
