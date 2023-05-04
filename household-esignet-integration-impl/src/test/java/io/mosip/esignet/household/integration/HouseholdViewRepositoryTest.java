package io.mosip.esignet.household.integration;

import io.mosip.esignet.household.integration.entity.HouseholdView;
import io.mosip.esignet.household.integration.repository.HouseholdViewRepository;
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
    public void createHouseholdView_withValidDetail_thenPass() {
//        HouseholdView householdView = new HouseholdView();
//        householdView.setHouseholdId(111111);
//        householdView.setGroupName("abc");
//        householdView.setPhoneNumber("123456890");
//        householdView.setIdNumber("H01");
//        householdView.setPassword("Household1");
//        householdView.setTamwiniConsented(true);
//        householdView = householdRepository.saveAndFlush(householdView);
//        Assert.assertNotNull(householdView);

       // Optional<HouseholdView> result = householdViewRepository.findById(1L);
        //Assert.assertTrue(result.isPresent());

//        result = householdRepository.findById(5L);
//        Assert.assertFalse(result.isPresent());

//        result = householdRepository.findByIdNumberAndPassword("H01", "Household1");
//        Assert.assertTrue(result.isPresent());
//
//        result = householdRepository.findByIdNumberAndPassword("H01", "Household2");
//        Assert.assertFalse(result.isPresent());

    }
}

//    @Test
//    public void createHouseholdView_withBlankHouseholdId_thenFail() {
//        HouseholdView householdView = new HouseholdView();
//        householdView.setHouseholdId(1111112);
//        householdView.setGroupName("abc");
//        householdView.setPhoneNumber("123456890");
//        householdView.setIdNumber("H01");
//        householdView.setPassword("Household1");
//        householdView.setTamwiniConsented(true);
//        try {
//            householdRepository.saveAndFlush(householdView);
//        } catch (ConstraintViolationException e) {
//            Assert.assertTrue(e.getConstraintViolations().stream()
//                    .anyMatch(v -> v.getPropertyPath().toString().equals("householdId")));
//            return;
//        }
//        Assert.fail();
//    }
//
//    @Test
//    public void createHouseholdView_withBlankIdNumber_thenFail() {
//        HouseholdView householdView = new HouseholdView();
//        householdView.setHouseholdId(111111);
//        householdView.setGroupName("abc");
//        householdView.setPhoneNumber("123456890");
//        householdView.setIdNumber(" ");
//        householdView.setPassword("Household1");
//        householdView.setTamwiniConsented(true);
//        try {
//            householdRepository.saveAndFlush(householdView);
//        } catch (ConstraintViolationException e) {
//            Assert.assertTrue(e.getConstraintViolations().stream()
//                    .anyMatch( v -> v.getPropertyPath().toString().equals("idNumber")));
//            return;
//        }
//        Assert.fail();
//    }
//
//    @Test
//    public void createHouseholdView_withNullIdNumber_thenFail() {
//        HouseholdView householdView = new HouseholdView();
//        householdView.setHouseholdId(111111);
//        householdView.setGroupName("abc");
//        householdView.setPhoneNumber("123456890");
//        householdView.setIdNumber(null);
//        householdView.setPassword("Household1");
//        householdView.setTamwiniConsented(true);
//        try {
//            householdRepository.saveAndFlush(householdView);
//        } catch (ConstraintViolationException e) {
//            Assert.assertTrue(e.getConstraintViolations().stream()
//                    .anyMatch( v -> v.getPropertyPath().toString().equals("idNumber")));
//            return;
//        }
//        Assert.fail();
//    }
//
//    @Test
//    public void createHouseholdView_withBlankGroupName_thenFail() {
//        HouseholdView householdView = new HouseholdView();
//        householdView.setHouseholdId(111111);
//        householdView.setGroupName(" ");
//        householdView.setPhoneNumber("123456890");
//        householdView.setIdNumber("H01");
//        householdView.setPassword("Household1");
//        householdView.setTamwiniConsented(true);
//        try {
//            householdRepository.saveAndFlush(householdView);
//        } catch (ConstraintViolationException e) {
//            Assert.assertTrue(e.getConstraintViolations().stream()
//                    .anyMatch( v -> v.getPropertyPath().toString().equals("groupName")));
//            return;
//        }
//        Assert.fail();
//    }
//    @Test
//    public void createHouseholdView_withBlankPhoneNumber_thenFail() {
//        HouseholdView householdView = new HouseholdView();
//        householdView.setHouseholdId(111111);
//        householdView.setGroupName("abc");
//        householdView.setPhoneNumber(" ");
//        householdView.setIdNumber("H01");
//        householdView.setPassword("Household1");
//        householdView.setTamwiniConsented(true);
//        try {
//            householdRepository.saveAndFlush(householdView);
//        } catch (ConstraintViolationException e) {
//            Assert.assertTrue(e.getConstraintViolations().stream()
//                    .anyMatch( v -> v.getPropertyPath().toString().equals("phoneNumber")));
//            return;
//        }
//        Assert.fail();
//    }
//
//    @Test
//    public void createHouseholdView_withBlankPassword_thenFail() {
//        HouseholdView householdView = new HouseholdView();
//        householdView.setHouseholdId(111111);
//        householdView.setGroupName("abc");
//        householdView.setPhoneNumber("1234567890");
//        householdView.setIdNumber("H01");
//        householdView.setPassword(" ");
//        householdView.setTamwiniConsented(true);
//        try {
//            householdRepository.saveAndFlush(householdView);
//        } catch (ConstraintViolationException e) {
//            Assert.assertTrue(e.getConstraintViolations().stream()
//                    .anyMatch( v -> v.getPropertyPath().toString().equals("password")));
//            return;
//        }
//        Assert.fail();
//    }
//
//    @Test
//    public void createHouseholdView_withNullPassword_thenFail() {
//        HouseholdView householdView = new HouseholdView();
//        householdView.setHouseholdId(111111);
//        householdView.setGroupName("abc");
//        householdView.setPhoneNumber("1234567890");
//        householdView.setIdNumber("H01");
//        householdView.setPassword(null);
//        householdView.setTamwiniConsented(true);
//        try {
//            householdRepository.saveAndFlush(householdView);
//        } catch (ConstraintViolationException | DataIntegrityViolationException e) {
//            Assert.assertTrue(true);
//            return;
//        }
//        Assert.fail();
//    }
//
//    @Test
//    public void createHouseholdView_withInvalidPassword_thenFail() {
//        HouseholdView householdView = new HouseholdView();
//        householdView.setHouseholdId(111111);
//        householdView.setGroupName("abc");
//        householdView.setPhoneNumber("1234567890");
//        householdView.setIdNumber("H01");
//        householdView.setPassword("house");
//        householdView.setTamwiniConsented(true);
//        try {
//            householdRepository.saveAndFlush(householdView);
//        } catch (ConstraintViolationException e) {
//            Assert.assertTrue(e.getConstraintViolations().stream()
//                    .anyMatch( v -> v.getPropertyPath().toString().equals("password")));
//            return;
//        }
//        Assert.fail();
//    }
//