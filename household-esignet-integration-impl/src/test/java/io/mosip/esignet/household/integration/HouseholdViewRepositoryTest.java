package io.mosip.esignet.household.integration;

import io.mosip.esignet.household.integration.entity.HouseholdView;
import io.mosip.esignet.household.integration.repository.HouseholdViewRepository;
import org.junit.Assert;
import org.junit.Before;
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

    @Before
    public void setUp() {
        HouseholdView householdView = new HouseholdView();
        householdView.setHouseholdId(1111112L);
        householdView.setGroupName("abc");
        householdView.setPhoneNumber("123456890");
        householdView.setIdNumber("H01");
        householdView.setPassword("Household1");
        householdView.setTamwiniConsented(true);
        householdView = householdViewRepository.saveAndFlush(householdView);
    }


    @Test
    public void householdView_withValidDetail_thenPass() {

        Optional<HouseholdView> result = householdViewRepository.findById(1111112L);
        Assert.assertTrue(result.isPresent());

        result = householdViewRepository.findById(11L);
        Assert.assertFalse(result.isPresent());

        result = householdViewRepository.findByIdNumberAndPassword("H01", "Household1");
        Assert.assertTrue(result.isPresent());

        result = householdViewRepository.findByIdNumberAndPassword("H01", "Household2");
        Assert.assertFalse(result.isPresent());

    }
}