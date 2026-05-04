/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet;

import io.mosip.esignet.config.AppConfig;
import io.mosip.esignet.core.constants.Constants;
import io.mosip.esignet.core.constants.ErrorConstants;
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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    @Test
    void serverProfile_InvalidFeature_ThrowsException() {
        ReflectionTestUtils.setField(appConfig, "serverProfile", "fapi2.0");
        io.mosip.esignet.entity.ServerProfile profileEntity = new io.mosip.esignet.entity.ServerProfile();
        profileEntity.setAdditionalConfigKey("require_pkce");
        profileEntity.setFeature("INVALID_FEATURE");
        when(serverProfileRepository.findByProfileName("fapi2.0"))
                .thenReturn(Collections.singletonList(profileEntity));

        EsignetException exception = assertThrows(EsignetException.class, () -> appConfig.serverProfile());
        assertEquals("INVALID_SERVER_PROFILE", exception.getMessage());
    }

    @Test
    void serverProfile_InvalidAdditionalConfigKey_ThrowsException() {
        ReflectionTestUtils.setField(appConfig, "serverProfile", "fapi2.0");
        io.mosip.esignet.entity.ServerProfile profileEntity = new io.mosip.esignet.entity.ServerProfile();
        profileEntity.setAdditionalConfigKey("invalid_config_key");
        profileEntity.setFeature("DPOP");
        when(serverProfileRepository.findByProfileName("fapi2.0"))
                .thenReturn(Collections.singletonList(profileEntity));

        EsignetException exception = assertThrows(EsignetException.class, () -> appConfig.serverProfile());
        assertEquals("INVALID_SERVER_PROFILE", exception.getMessage());
    }

    @Test
    void serverProfile_ValidFapi2Profile_ReturnsProfileWithAllFeatures() throws EsignetException {
        ReflectionTestUtils.setField(appConfig, "serverProfile", "fapi2.0");
        setupValidCacheExpireInSeconds();
        setupValidSigningAlgorithms();
        setupFapi2Profile();

        ServerProfile result = appConfig.serverProfile();

        assertNotNull(result);
        assertEquals("fapi2.0", result.getName());
        assertEquals(3, result.getFeatureMap().size());
    }

    @Test
    void serverProfile_Fapi2WithAuthCodeExpiryExceeds60_ThrowsException() {
        ReflectionTestUtils.setField(appConfig, "serverProfile", "fapi2.0");
        Map<String, Integer> cacheExpireInSeconds = new HashMap<>();
        cacheExpireInSeconds.put(Constants.AUTH_CODE_GENERATED_CACHE, 120);
        ReflectionTestUtils.setField(appConfig, "cacheExpireInSeconds", cacheExpireInSeconds);
        setupValidSigningAlgorithms();
        setupFapi2Profile();

        EsignetException exception = assertThrows(EsignetException.class, () -> appConfig.serverProfile());
        assertEquals(ErrorConstants.INVALID_AUTH_CODE_EXPIRY_FOR_FAPI2, exception.getErrorCode());
    }

    @Test
    void serverProfile_NonFapi2WithAuthCodeExpiryExceeds60_thenPass() throws EsignetException {
        ReflectionTestUtils.setField(appConfig, "serverProfile", "gov");
        Map<String, Integer> cacheExpireInSeconds = new HashMap<>();
        cacheExpireInSeconds.put(Constants.AUTH_CODE_GENERATED_CACHE, 300);
        ReflectionTestUtils.setField(appConfig, "cacheExpireInSeconds", cacheExpireInSeconds);

        io.mosip.esignet.entity.ServerProfile pkceProfile = new io.mosip.esignet.entity.ServerProfile();
        pkceProfile.setAdditionalConfigKey("require_pkce");
        pkceProfile.setFeature("PKCE");
        when(serverProfileRepository.findByProfileName("gov"))
                .thenReturn(Collections.singletonList(pkceProfile));

        ServerProfile result = appConfig.serverProfile();
        assertNotNull(result);
        assertEquals("gov", result.getName());
    }

    @Test
    void serverProfile_Fapi2WithRS256Algorithm_ThrowsException() {
        ReflectionTestUtils.setField(appConfig, "serverProfile", "fapi2.0");
        setupValidCacheExpireInSeconds();
        List<String> signingAlgorithms = Arrays.asList("RS256", "PS256", "ES256");
        ReflectionTestUtils.setField(appConfig, "supportedSigningAlgorithms", signingAlgorithms);
        setupFapi2Profile();

        EsignetException exception = assertThrows(EsignetException.class, () -> appConfig.serverProfile());
        assertEquals(ErrorConstants.UNSUPPORTED_ALGORITHM_FOR_FAPI2, exception.getErrorCode());
    }

    @Test
    void serverProfile_NonFapi2WithRS256Algorithm_thenPass() throws EsignetException {
        ReflectionTestUtils.setField(appConfig, "serverProfile", "gov");
        setupValidCacheExpireInSeconds();
        List<String> signingAlgorithms = Arrays.asList("RS256", "PS256", "ES256");
        ReflectionTestUtils.setField(appConfig, "supportedSigningAlgorithms", signingAlgorithms);

        io.mosip.esignet.entity.ServerProfile pkceProfile = new io.mosip.esignet.entity.ServerProfile();
        pkceProfile.setAdditionalConfigKey("require_pkce");
        pkceProfile.setFeature("PKCE");
        when(serverProfileRepository.findByProfileName("gov"))
                .thenReturn(Collections.singletonList(pkceProfile));

        ServerProfile result = appConfig.serverProfile();
        assertNotNull(result);
        assertEquals("gov", result.getName());
    }

    private void setupValidCacheExpireInSeconds() {
        Map<String, Integer> cacheExpireInSeconds = new HashMap<>();
        cacheExpireInSeconds.put(Constants.AUTH_CODE_GENERATED_CACHE, 60);
        ReflectionTestUtils.setField(appConfig, "cacheExpireInSeconds", cacheExpireInSeconds);
    }

    private void setupValidSigningAlgorithms() {
        List<String> signingAlgorithms = Arrays.asList("PS256", "ES256");
        ReflectionTestUtils.setField(appConfig, "supportedSigningAlgorithms", signingAlgorithms);
    }

    private void setupFapi2Profile() {
        io.mosip.esignet.entity.ServerProfile dpopProfile = new io.mosip.esignet.entity.ServerProfile();
        dpopProfile.setAdditionalConfigKey("dpop_bound_access_tokens");
        dpopProfile.setFeature("DPOP");

        io.mosip.esignet.entity.ServerProfile parProfile = new io.mosip.esignet.entity.ServerProfile();
        parProfile.setAdditionalConfigKey("require_pushed_authorization_requests");
        parProfile.setFeature("PAR");

        io.mosip.esignet.entity.ServerProfile pkceProfile = new io.mosip.esignet.entity.ServerProfile();
        pkceProfile.setAdditionalConfigKey("require_pkce");
        pkceProfile.setFeature("PKCE");

        when(serverProfileRepository.findByProfileName("fapi2.0"))
                .thenReturn(Arrays.asList(dpopProfile, parProfile, pkceProfile));
    }

}

