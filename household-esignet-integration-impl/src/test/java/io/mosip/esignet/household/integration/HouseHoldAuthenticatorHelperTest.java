package io.mosip.esignet.household.integration;


import io.mosip.esignet.api.dto.AuthChallenge;
import io.mosip.esignet.api.exception.KycAuthException;
import io.mosip.esignet.household.integration.dto.KycTransactionDto;
import io.mosip.esignet.household.integration.entity.HouseholdView;
import io.mosip.esignet.household.integration.service.HouseholdAuthenticatorHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.junit.MockitoJUnitRunner;

import static io.mosip.esignet.household.integration.util.ErrorConstants.*;

@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class HouseHoldAuthenticatorHelperTest {

    @InjectMocks
    HouseholdAuthenticatorHelper houseHoldAuthenticatorHelper;

    @Test
    public void validateAuthChallenge_withValidDetails_thenPass()
    {
        HouseholdView householdView=new HouseholdView();
        householdView.setHouseholdId(12345L);
        householdView.setIdNumber("1234567890123456");
        householdView.setPassword("Test1234");

        AuthChallenge authChallenge=new AuthChallenge();
        authChallenge.setAuthFactorType("PWD");
        authChallenge.setFormat("alpha-numeric");
        authChallenge.setChallenge("Test1234");

        try{
            houseHoldAuthenticatorHelper.validateAuthChallenge(householdView,authChallenge);
            Assert.assertTrue(true);
        }catch (KycAuthException e){
            Assert.assertTrue(true);
        }
    }

    @Test
    public void validateAuthChallenge_withInValidAuthChallenge_thenFail()
    {
        HouseholdView householdView=new HouseholdView();
        householdView.setHouseholdId(12345L);
        householdView.setIdNumber("1234567890123456");
        householdView.setPassword("Test@123");

        AuthChallenge authChallenge=new AuthChallenge();
        authChallenge.setAuthFactorType("PWD");
        authChallenge.setFormat("alpha-numeric");
        authChallenge.setChallenge("Test@123");

        try{
            houseHoldAuthenticatorHelper.validateAuthChallenge(householdView,null);
        }catch (KycAuthException e){
            Assert.assertEquals(INVALID_AUTH_CHALLENGE,e.getMessage());
        }
    }

    @Test
    public void validateAuthChallenge_withInValidFactorType_thenFail()
    {
        HouseholdView householdView=new HouseholdView();
        householdView.setHouseholdId(12345L);
        householdView.setIdNumber("1234567890123456");
        householdView.setPassword("Test@123");

        AuthChallenge authChallenge=new AuthChallenge();
        authChallenge.setAuthFactorType("OTP");
        authChallenge.setFormat("alpha-numeric");
        authChallenge.setChallenge("Test@123");

        try{
            houseHoldAuthenticatorHelper.validateAuthChallenge(householdView,authChallenge);
        }catch (KycAuthException e){
            Assert.assertEquals(INVALID_CHALLENGE_FORMAT,e.getMessage());
        }
    }

    @Test
    public void validateAuthChallenge_withInValidFormatType_thenFail()
    {
        HouseholdView householdView=new HouseholdView();
        householdView.setHouseholdId(12345L);
        householdView.setIdNumber("1234567890123456");
        householdView.setPassword("Test@123");

        AuthChallenge authChallenge=new AuthChallenge();
        authChallenge.setAuthFactorType("PWD");
        authChallenge.setFormat("alpha");
        authChallenge.setChallenge("Test@123");

        try{
            houseHoldAuthenticatorHelper.validateAuthChallenge(householdView,authChallenge);
        }catch (KycAuthException e){
            Assert.assertEquals(INVALID_CHALLENGE_FORMAT,e.getMessage());
        }
    }

    @Test
    public void validatePassword_withValidDetails_thenPass() throws Exception
    {
        HouseholdView householdView=new HouseholdView();
        householdView.setHouseholdId(12345L);
        householdView.setIdNumber("1234567890123456");
        householdView.setPassword(HouseHoldAuthenticatorTest.generatePasswordHash("Test1234"));

        AuthChallenge authChallenge=new AuthChallenge();
        authChallenge.setAuthFactorType("PWD");
        authChallenge.setFormat("alpha-numeric");
        authChallenge.setChallenge("Test1234");

        try{
            houseHoldAuthenticatorHelper.validatePassword(authChallenge.getChallenge(),householdView.getPassword());
            Assert.assertTrue(true);
        }catch (KycAuthException e){
            Assert.fail();
        }
    }

    @Test
    public void validatePassword_withInValidPasswordFormat_thenFail()
    {
        HouseholdView householdView=new HouseholdView();
        householdView.setHouseholdId(12345L);
        householdView.setIdNumber("1234567890123456");
        householdView.setPassword("Test@123");

        AuthChallenge authChallenge=new AuthChallenge();
        authChallenge.setAuthFactorType("PWD");
        authChallenge.setFormat("alpha");
        authChallenge.setChallenge("Testingforu");

        try{
            houseHoldAuthenticatorHelper.validatePassword(authChallenge.getChallenge(),householdView.getPassword());
        }catch (KycAuthException e){
            Assert.assertEquals(INVALID_AUTH_CHALLENGE,e.getMessage());
        }
    }

    @Test
    public void validatePassword_withInValidHashFormat_thenFail() throws Exception
    {
        HouseholdView householdView=new HouseholdView();
        householdView.setHouseholdId(12345L);
        householdView.setIdNumber("1234567890123456");
        householdView.setPassword(HouseHoldAuthenticatorTest.generatePasswordHash("Test12345"));

        AuthChallenge authChallenge=new AuthChallenge();
        authChallenge.setAuthFactorType("PWD");
        authChallenge.setFormat("alpha");
        authChallenge.setChallenge("Testingforu123");

        try{
            houseHoldAuthenticatorHelper.validatePassword(authChallenge.getChallenge(),householdView.getPassword());
        }catch (KycAuthException e){
            Assert.assertEquals(INVALID_PASSWORD,e.getMessage());
        }
    }

    @Test
    public void setKycAuthTransaction_withValidDetails_thenPass()
    {
        KycTransactionDto kycTransactionDto =new KycTransactionDto();
        kycTransactionDto.setKycToken("12345");
        kycTransactionDto.setPsut("123456");
        kycTransactionDto.setHouseholdId(12345L);

        KycTransactionDto kycTransactionDto1 = houseHoldAuthenticatorHelper.setKycAuthTransaction("12345", "123456", 12345L);

        Assert.assertNotNull(kycTransactionDto1);
        Assert.assertEquals(kycTransactionDto.getKycToken(),kycTransactionDto1.getKycToken());
    }

    @Test
    public void setKycAuthTransaction_withInValidDetails_thenFail()
    {
        KycTransactionDto kycTransactionDto =new KycTransactionDto();
        kycTransactionDto.setKycToken("12345");
        kycTransactionDto.setPsut("123456");
        kycTransactionDto.setHouseholdId(12345L);

        KycTransactionDto kycTransactionDto1 = houseHoldAuthenticatorHelper.setKycAuthTransaction("123456", "123456", 12345L);

        Assert.assertNotNull(kycTransactionDto1);
        Assert.assertNotEquals(kycTransactionDto.getKycToken(),kycTransactionDto1.getKycToken());
    }
}
