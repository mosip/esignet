package io.mosip.esignet.household.integration.repository;

import io.mosip.esignet.household.integration.entity.HouseholdView;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HouseholdRepository extends JpaRepository<HouseholdView,Long> {
     // HouseholdView findByPhoneNumber(String phoneNumber);
    HouseholdView findByIdNumberAndPassword(String idNumber, String password);

}
