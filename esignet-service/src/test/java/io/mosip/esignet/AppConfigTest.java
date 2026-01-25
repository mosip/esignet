package io.mosip.esignet;

import io.mosip.esignet.config.AppConfig;
import io.mosip.esignet.core.dto.ServerProfile;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.repository.ServerProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppConfigTest {

    @Mock
    private ServerProfileRepository serverProfileRepository;

    @InjectMocks
    private AppConfig appConfig;

    @Value("${mosip.esignet.server.profile:none}")
    private String serverProfile;

    @Test
    void serverProfile_ServerProfileIsNone_returnsWithNoFeatures() throws EsignetException {
        ReflectionTestUtils.setField(appConfig, "serverProfile", "none");

        ServerProfile result = appConfig.serverProfile();

        assertNotNull(result);
        assertEquals("none", result.getName());
        assertTrue(result.getFeatureMap().isEmpty());
    }

    @Test
    void serverProfile_NoProfilesFound_ThrowsException() {
        ReflectionTestUtils.setField(appConfig, "serverProfile", "invalidProfile");
        when(serverProfileRepository.findByProfileName("invalidProfile")).thenReturn(Collections.emptyList());

        EsignetException exception = assertThrows(EsignetException.class, () -> appConfig.serverProfile());
        assertEquals("INVALID_SERVER_PROFILE", exception.getMessage());
    }

    @Test
    void serverProfile_NullProfile_ThrowsException() {
        ReflectionTestUtils.setField(appConfig, "serverProfile", null);

        EsignetException exception = assertThrows(EsignetException.class, () -> appConfig.serverProfile());
        assertEquals("INVALID_SERVER_PROFILE", exception.getMessage());
    }

    @Test
    void serverProfile_ProfilesExist_ReturnsProfileWithFeatures() throws EsignetException {
        ReflectionTestUtils.setField(appConfig, "serverProfile", "gov");
        io.mosip.esignet.entity.ServerProfile profileEntity = new io.mosip.esignet.entity.ServerProfile();
        profileEntity.setAdditionalConfigKey("require_pkce");
        profileEntity.setFeature("PKCE");
        when(serverProfileRepository.findByProfileName("gov"))
                .thenReturn(Collections.singletonList(profileEntity));

        ServerProfile result = appConfig.serverProfile();

        assertNotNull(result);
        assertEquals("gov", result.getName());
        assertEquals(1, result.getFeatureMap().size());
        assertEquals("PKCE", result.getFeatureMap().get("require_pkce"));
    }
}

