package io.mosip.esignet.household.integration;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.esignet.api.dto.*;
import io.mosip.esignet.api.exception.KycExchangeException;
import io.mosip.esignet.household.integration.dto.KycTransactionDto;
import io.mosip.esignet.household.integration.entity.HouseholdView;
import io.mosip.esignet.household.integration.repository.HouseholdViewRepository;
import io.mosip.esignet.household.integration.service.HouseholdAuthenticator;
import io.mosip.esignet.household.integration.service.HouseholdAuthenticatorHelper;
import io.mosip.esignet.household.integration.util.ErrorConstants;
import io.mosip.kernel.signature.dto.JWTSignatureResponseDto;
import io.mosip.kernel.signature.service.SignatureService;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class HouseHoldAuthenticatorTest {

    @Mock
    private HouseholdViewRepository householdViewRepository;

    @Mock
    private  HouseholdAuthenticatorHelper householdAuthenticatorHelper;

    @Mock
    ObjectMapper objectMapper;

    @Mock
    SignatureService signatureService;

    @InjectMocks
    private HouseholdAuthenticator householdAuthenticator;


    @Test
    public void doKycAuth_withValidDetail_thenPass() throws Exception {
        KycAuthDto kycAuthDto= new KycAuthDto();
        kycAuthDto.setTransactionId("12345");
        kycAuthDto.setIndividualId("12345");

        AuthChallenge authChallenge= new AuthChallenge();
        authChallenge.setAuthFactorType("PWD");
        authChallenge.setFormat("alpha-numeric");
        authChallenge.setChallenge("123456789ABC");
        kycAuthDto.setChallengeList(Arrays.asList(authChallenge));

        HouseholdView householdView= new HouseholdView();
        householdView.setGroupName("group1");
        householdView.setIdNumber("12345");
        householdView.setPhoneNumber("12345");
        householdView.setPassword(generatePasswordHash("123456789ABC"));

        Mockito.when(householdViewRepository.findByIdNumber("12345")).thenReturn(Optional.of(householdView));

        KycTransactionDto kycTransactionDto=new KycTransactionDto();
        kycTransactionDto.setKycToken("12345");
        kycTransactionDto.setPsut("12345");
        kycTransactionDto.setHouseholdId(householdView.getHouseholdId());

        Mockito.when(householdAuthenticatorHelper.setKycAuthTransaction(Mockito.anyString(),
                Mockito.anyString(),Mockito.anyLong())).thenReturn(kycTransactionDto);

        KycAuthResult kycAuthResult= householdAuthenticator.
                        doKycAuth("12345","12345", kycAuthDto);
        Assert.assertNotNull(kycAuthResult);
        Assert.assertEquals(kycAuthResult.getKycToken(),"12345");
        Assert.assertEquals(kycAuthResult.getPartnerSpecificUserToken(),"12345");
    }

    @Test
    public void doKycAuth_withInValid_IndividualId__thenFail() {
        KycAuthDto kycAuthDto= new KycAuthDto();
        kycAuthDto.setTransactionId("1234567890");
        kycAuthDto.setIndividualId("12345");

        AuthChallenge authChallenge= new AuthChallenge();
        authChallenge.setAuthFactorType("PWD");
        authChallenge.setFormat("alpha-numeric");
        authChallenge.setChallenge("123456789ABC");
        kycAuthDto.setChallengeList(Arrays.asList(authChallenge));

        Optional<HouseholdView> householdViewOptional = Optional.empty();
        Mockito.when(householdViewRepository.findByIdNumber("12345")).thenReturn(householdViewOptional);

        try{
            householdAuthenticator.
                    doKycAuth("1234567890","123456789123456789", kycAuthDto);
        }
        catch (Exception e){
            Assert.assertEquals(ErrorConstants.INVALID_INDIVIDUAL_ID,e.getMessage());
        }
    }

    @Test
    public void doKycAuth_withInValidAuthChallenge_thenFail() {
        KycAuthDto kycAuthDto= new KycAuthDto();
        kycAuthDto.setTransactionId("1234567890");
        kycAuthDto.setIndividualId("123456789123456789");

        AuthChallenge authChallenge= new AuthChallenge();
        authChallenge.setAuthFactorType("OTP");
        authChallenge.setFormat("alpha-numeric");
        authChallenge.setChallenge("123456");
        //kycAuthDto.setChallengeList(Arrays.asList(authChallenge));
        try{
            householdAuthenticator.
                    doKycAuth("1234567890","123456789123456789", kycAuthDto);
        }
        catch (Exception e){
            Assert.assertEquals(ErrorConstants.INVALID_AUTH_CHALLENGE,e.getMessage());
        }
    }

    @Test
    public void doKycExchange_withInValidJwtToken_thenFail()
    {
        KycExchangeDto kycExchangeDto=new KycExchangeDto();
        kycExchangeDto.setTransactionId("1234567890");
        kycExchangeDto.setKycToken("1234567890");

        KycTransactionDto kycTransactionDto= new KycTransactionDto();
        kycTransactionDto.setHouseholdId(12345L);
        Mockito.when(householdAuthenticatorHelper.getKycAuthTransaction("1234567890")).thenReturn(kycTransactionDto);
        try {
            householdAuthenticator.
                    doKycExchange("1234567890", "123456789123456789", kycExchangeDto);
        }catch (KycExchangeException ex)
        {
            Assert.assertEquals(ErrorConstants.KYC_EXCHANGE_FAILED,ex.getMessage());
        }
    }

    @Test
    public void doKycExchange_withValidJwtToken_thenPass() throws Exception
    {
        KycExchangeDto kycExchangeDto=new KycExchangeDto();
        kycExchangeDto.setTransactionId("1234567890");
        kycExchangeDto.setKycToken("1234567890");

        KycTransactionDto kycTransactionDto= new KycTransactionDto();
        kycTransactionDto.setHouseholdId(12345L);
        Mockito.when(householdAuthenticatorHelper.getKycAuthTransaction(Mockito.anyString())).thenReturn(kycTransactionDto);

        String payload="abc";
        Mockito.when(objectMapper.writeValueAsString(Mockito.anyMap())).thenReturn(payload);

        JWTSignatureResponseDto jwtSignatureResponseDto= new JWTSignatureResponseDto();
        jwtSignatureResponseDto.setJwtSignedData("1234567890");
        Mockito.when(signatureService.jwtSign(Mockito.any())).thenReturn(jwtSignatureResponseDto);

        KycExchangeResult kycExchangeResult = householdAuthenticator.
                doKycExchange("1234567890", "123456789123456789", kycExchangeDto);
        Assert.assertNotNull(kycExchangeResult);
        Assert.assertEquals(kycExchangeDto.getKycToken(),"1234567890");
    }

    @Test
    public void doKycExchange_withInValidKycToken_thenFail()
    {
        KycExchangeDto kycExchangeDto=new KycExchangeDto();
        kycExchangeDto.setTransactionId("1234567890");
        kycExchangeDto.setKycToken("12345");

         try {
             householdAuthenticator.
                     doKycExchange("1234567890", "123456789123456789", kycExchangeDto);
         }catch (KycExchangeException ex)
         {
             Assert.assertEquals(ErrorConstants.INVALID_TRANSACTION,ex.getMessage());
         }

    }
    @Test
    public void isSupportedOtpChannel_thenPass()
    {
        Assert.assertFalse(householdAuthenticator.isSupportedOtpChannel("SMS"));
    }

    //to generate hash password for testing
    public static String generatePasswordHash(String password) throws Exception {
        String pattern = "$Hmac-%s$%d$%s$%s$";
        String algo = "SHA512";
        int iterations = 25000;
        String salt = Base64.getEncoder().encodeToString("123123123123".getBytes(StandardCharsets.UTF_8));
        int keyLength = 512;
        SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmac"+algo);
        PBEKeySpec spec = new PBEKeySpec(
                password.toCharArray(),
                Base64.getDecoder().decode(salt.replace(".", "+")),
                iterations,
                keyLength
        );
        SecretKey secretKey = skf.generateSecret(spec);
        String computedHash = Base64.getEncoder().encodeToString(secretKey.getEncoded());
        computedHash = computedHash.replace("+", ".");
        computedHash = computedHash.replaceAll("=+$", "");
        return String.format(pattern, algo, iterations, salt, computedHash);
    }

}
