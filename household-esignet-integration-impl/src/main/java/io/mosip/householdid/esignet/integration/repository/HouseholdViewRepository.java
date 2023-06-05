package io.mosip.householdid.esignet.integration.repository;

import io.mosip.householdid.esignet.integration.entity.HouseholdView;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

import org.springframework.stereotype.Repository;


@Repository
public interface HouseholdViewRepository extends JpaRepository<HouseholdView, Long> {
    Optional<HouseholdView> findByIdNumber(String idNumber);
}
