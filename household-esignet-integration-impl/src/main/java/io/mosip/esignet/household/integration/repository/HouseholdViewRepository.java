package io.mosip.esignet.household.integration.repository;

import io.mosip.esignet.household.integration.entity.HouseholdView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
@Repository
public interface HouseholdViewRepository extends JpaRepository<HouseholdView, Long> {
    Optional<HouseholdView> findByIdNumber(String idNumber);
}
