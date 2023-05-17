package io.mosip.householdid.esignet.integration;

import io.mosip.householdid.esignet.integration.entity.HouseholdView;
import io.mosip.householdid.esignet.integration.repository.HouseholdViewRepository;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.junit4.SpringRunner;
import java.util.Optional;

@RunWith(SpringRunner.class)
@DataJpaTest
public class HouseholdViewRepositoryTest {
    @Autowired
    private HouseholdViewRepository householdViewRepository;

    @Test
    public void householdView_withValidDetail_thenPass() {

        Optional<HouseholdView> result = householdViewRepository.findById(1111112L);
        Assert.assertTrue(result.isPresent());

        result = householdViewRepository.findById(11L);
        Assert.assertFalse(result.isPresent());

        result = householdViewRepository.findByIdNumber("H01");
        Assert.assertTrue(result.isPresent());

    }
}