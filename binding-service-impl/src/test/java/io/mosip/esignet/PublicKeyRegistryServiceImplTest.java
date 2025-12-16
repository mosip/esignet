package io.mosip.esignet;

import io.mosip.esignet.entity.PublicKeyRegistry;
import io.mosip.esignet.repository.PublicKeyRegistryRepository;
import io.mosip.esignet.services.PublicKeyRegistryServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

@Slf4j
@ExtendWith(MockitoExtension.class)
public class PublicKeyRegistryServiceImplTest {

    @Mock
    PublicKeyRegistryRepository publicKeyRegistryRepository;

    @InjectMocks
    PublicKeyRegistryServiceImpl publicKeyRegistryService;

    @Test
    public void findLatestPublicKeyByPsuTokenAndAuthFactor_WithValidDetail_ThenPass() {
        PublicKeyRegistry publicKeyRegistry = new PublicKeyRegistry();
        publicKeyRegistry.setPublicKey("publicKey");
        publicKeyRegistry.setPsuToken("pusToke");
        publicKeyRegistry.setThumbprint("thumbprint");
        publicKeyRegistry.setPublicKeyHash("hase");
        publicKeyRegistry.setCertificate("cert");
        Mockito.when(publicKeyRegistryRepository.findLatestByPsuTokenAndAuthFactor(Mockito.anyString(), Mockito.anyString())).thenReturn(Optional.of(publicKeyRegistry));

        Optional<io.mosip.esignet.core.dto.PublicKeyRegistry> publicKeyRegistryOptional = publicKeyRegistryService.findLatestPublicKeyByPsuTokenAndAuthFactor(Mockito.anyString(), Mockito.anyString());
        Assertions.assertEquals(publicKeyRegistryOptional.get().getPublicKey(), publicKeyRegistry.getPublicKey());
        Assertions.assertEquals(publicKeyRegistryOptional.get().getPsuToken(), publicKeyRegistry.getPsuToken());
    }

    @Test
    public void findLatestPublicKeyByPsuTokenAndAuthFactor_WithInValidDetail_ThenFail() {
        Mockito.when(publicKeyRegistryRepository.findLatestByPsuTokenAndAuthFactor(Mockito.anyString(), Mockito.anyString())).thenReturn(Optional.empty());

        Optional<io.mosip.esignet.core.dto.PublicKeyRegistry> publicKeyRegistryOptional = publicKeyRegistryService.findLatestPublicKeyByPsuTokenAndAuthFactor(Mockito.anyString(), Mockito.anyString());
        Assertions.assertEquals(Optional.empty(), publicKeyRegistryOptional);
    }

    @Test
    public void findFirstByIdHashAndThumbprintAndExpiredtimes_WithValidDetail_ThenPass() {
        PublicKeyRegistry publicKeyRegistry = new PublicKeyRegistry();
        publicKeyRegistry.setPublicKey("publicKey");
        publicKeyRegistry.setPsuToken("pusToke");
        publicKeyRegistry.setThumbprint("thumbprint");
        publicKeyRegistry.setPublicKeyHash("hase");
        publicKeyRegistry.setCertificate("cert");
        Mockito.when(publicKeyRegistryRepository.findFirstByIdHashAndThumbprintAndExpiredtimesGreaterThanOrderByExpiredtimesDesc(Mockito.anyString(), Mockito.anyString(), Mockito.any())).thenReturn(Optional.of(publicKeyRegistry));

        Optional<io.mosip.esignet.core.dto.PublicKeyRegistry> publicKeyRegistryOptional = publicKeyRegistryService.findFirstByIdHashAndThumbprintAndExpiredtimes("idHash", "thumbprint");
        Assertions.assertEquals(publicKeyRegistryOptional.get().getPublicKey(), publicKeyRegistry.getPublicKey());
        Assertions.assertEquals(publicKeyRegistryOptional.get().getPsuToken(), publicKeyRegistry.getPsuToken());
    }

    @Test
    public void findFirstByIdHashAndThumbprintAndExpiredtimes_WithInValidDetail_ThenFail() {
        Mockito.when(publicKeyRegistryRepository.findFirstByIdHashAndThumbprintAndExpiredtimesGreaterThanOrderByExpiredtimesDesc(Mockito.anyString(), Mockito.anyString(), Mockito.any())).thenReturn(Optional.empty());

        Optional<io.mosip.esignet.core.dto.PublicKeyRegistry> publicKeyRegistryOptional = publicKeyRegistryService.findFirstByIdHashAndThumbprintAndExpiredtimes("idHash", "thumbprint");
        Assertions.assertEquals(Optional.empty(), publicKeyRegistryOptional);
    }
}
