package io.mosip.esignet.repository;

import io.mosip.esignet.entity.Consent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ConsentRepository extends JpaRepository<Consent, UUID> {
    Optional<Consent> findFirstByClientIdAndPsuValueOrderByCreatedOnDesc(String clientId, String psuValue);
}
