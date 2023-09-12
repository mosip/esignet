package io.mosip.esignet;

import io.mosip.esignet.entity.PublicKeyRegistry;
import io.mosip.esignet.repository.PublicKeyRegistryRepository;
import io.mosip.esignet.services.PublicKeyRegistryServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import java.util.Optional;

@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class PublicKeyRegistryServiceImplTest {

    @Mock
    PublicKeyRegistryRepository publicKeyRegistryRepository;

    @InjectMocks
    PublicKeyRegistryServiceImpl publicKeyRegistryService;

    @Test
    public void findLatestPublicKeyByPsuTokenAndAuthFactor_WithValidDetail_ThenPass(){
        PublicKeyRegistry publicKeyRegistry=new PublicKeyRegistry();
        publicKeyRegistry.setPublicKey("publicKey");
        publicKeyRegistry.setPsuToken("pusToke");
        publicKeyRegistry.setThumbprint("thumbprint");
        publicKeyRegistry.setPublicKeyHash("hase");
        publicKeyRegistry.setCertificate("cert");
        Mockito.when(publicKeyRegistryRepository.findLatestByPsuTokenAndAuthFactor(Mockito.anyString(),Mockito.anyString())).thenReturn(Optional.of(publicKeyRegistry));

        Optional<io.mosip.esignet.core.dto.PublicKeyRegistry>publicKeyRegistryOptional= publicKeyRegistryService.findLatestPublicKeyByPsuTokenAndAuthFactor(Mockito.anyString(),Mockito.anyString());
        Assert.assertEquals(publicKeyRegistryOptional.get().getPublicKey(),publicKeyRegistry.getPublicKey());
        Assert.assertEquals(publicKeyRegistryOptional.get().getPsuToken(),publicKeyRegistry.getPsuToken());
    }

    @Test
    public void findLatestPublicKeyByPsuTokenAndAuthFactor_WithInValidDetail_ThenFail(){
        Mockito.when(publicKeyRegistryRepository.findLatestByPsuTokenAndAuthFactor(Mockito.anyString(),Mockito.anyString())).thenReturn(Optional.empty());

        Optional<io.mosip.esignet.core.dto.PublicKeyRegistry>publicKeyRegistryOptional= publicKeyRegistryService.findLatestPublicKeyByPsuTokenAndAuthFactor(Mockito.anyString(),Mockito.anyString());
        Assert.assertEquals(Optional.empty(),publicKeyRegistryOptional);
    }

    @Test
    public void findFirstByIdHashAndThumbprintAndExpiredtimes_WithValidDetail_ThenPass(){
        PublicKeyRegistry publicKeyRegistry=new PublicKeyRegistry();
        publicKeyRegistry.setPublicKey("publicKey");
        publicKeyRegistry.setPsuToken("pusToke");
        publicKeyRegistry.setThumbprint("thumbprint");
        publicKeyRegistry.setPublicKeyHash("hase");
        publicKeyRegistry.setCertificate("cert");
        Mockito.when(publicKeyRegistryRepository.findFirstByIdHashAndThumbprintAndExpiredtimesGreaterThanOrderByExpiredtimesDesc(Mockito.anyString(),Mockito.anyString(),Mockito.any())).thenReturn(Optional.of(publicKeyRegistry));

        Optional<io.mosip.esignet.core.dto.PublicKeyRegistry>publicKeyRegistryOptional= publicKeyRegistryService.findFirstByIdHashAndThumbprintAndExpiredtimes("idHash","thumbprint");
        Assert.assertEquals(publicKeyRegistryOptional.get().getPublicKey(),publicKeyRegistry.getPublicKey());
        Assert.assertEquals(publicKeyRegistryOptional.get().getPsuToken(),publicKeyRegistry.getPsuToken());
    }

    @Test
    public void findFirstByIdHashAndThumbprintAndExpiredtimes_WithInValidDetail_ThenFail(){
        Mockito.when(publicKeyRegistryRepository.findFirstByIdHashAndThumbprintAndExpiredtimesGreaterThanOrderByExpiredtimesDesc(Mockito.anyString(),Mockito.anyString(),Mockito.any())).thenReturn(Optional.empty());

        Optional<io.mosip.esignet.core.dto.PublicKeyRegistry>publicKeyRegistryOptional= publicKeyRegistryService.findFirstByIdHashAndThumbprintAndExpiredtimes("idHash","thumbprint");
        Assert.assertEquals(Optional.empty(),publicKeyRegistryOptional);
    }
}
