package io.mosip.idp;

import io.mosip.idp.domain.ClientDetail;
import io.mosip.idp.repositories.ClientDetailRepository;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Optional;

@SpringBootTest
@ActiveProfiles("test")
@RunWith(SpringRunner.class)
public class ClientDetailRepositoryTest {

    @Autowired
    private ClientDetailRepository clientDetailRepository;

    @Test
    public void createClientDetail_withValidDetail_thenPass() {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setId("C01");
        clientDetail.setName("Client-01");
        clientDetail.setLogoUri("https://clienapp.com/logo.png");
        clientDetail.setStatus("ACTIVE");
        clientDetail.setRedirectUris("https://clientapp.com/home,https://clientapp.com/home2");
        clientDetail.setCertData("DUMMY PEM CERT");
        clientDetail.setCertThumbprint("THUMBPRINT");
        clientDetail.setRpId("RP01");
        clientDetail.setClaims("{}");
        clientDetail.setAmrValues("{}");
        clientDetail = clientDetailRepository.save(clientDetail);
        Assert.assertNotNull(clientDetail);

        Optional<ClientDetail> result = clientDetailRepository.findById("C01");
        Assert.assertTrue(result.isPresent());

        result = clientDetailRepository.findById("C02");
        Assert.assertFalse(result.isPresent());

        result = clientDetailRepository.findByIdAndStatus("C01", "ACTIVE");
        Assert.assertTrue(result.isPresent());

        result = clientDetailRepository.findByIdAndStatus("C01", "INACTIVE");
        Assert.assertFalse(result.isPresent());
    }

    @Test
    public void createClientDetail_withBlankClientId_thenFail() {

    }

    @Test
    public void createClientDetail_withBlankPublicKey_thenFail() {

    }

    @Test
    public void createClientDetail_withBlankClientName_thenFail() {

    }

    @Test
    public void createClientDetail_withBlankRP_thenFail() {

    }

    @Test
    public void createClientDetail_withBlankRedirectUri_thenFail() {

    }

    @Test
    public void createClientDetail_withBlankStatus_thenFail() {

    }

    @Test
    public void createClientDetail_withInvalidStatus_thenFail() {

    }
}
