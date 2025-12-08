package io.mosip.esignet.services;

import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.entity.OpenIdProfile;
import io.mosip.esignet.repository.OpenIdProfileRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Slf4j
public class OpenIdProfileServiceImplTest {

    @Mock
    private OpenIdProfileRepository openIdProfileRepository;

    @InjectMocks
    private OpenIdProfileServiceImpl openIdProfileService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void getFeaturesByProfileName_thenPass() {
        OpenIdProfile profile1 = mock(OpenIdProfile.class);
        OpenIdProfile profile2 = mock(OpenIdProfile.class);
        when(profile1.getFeature()).thenReturn("feature1");
        when(profile2.getFeature()).thenReturn("feature2");
        when(openIdProfileRepository.findByProfileName("profileA"))
                .thenReturn(Arrays.asList(profile1, profile2));

        List<String> features = openIdProfileService.getFeaturesByProfileName("profileA");
        assertEquals(2, features.size());
        assertTrue(features.contains("feature1"));
        assertTrue(features.contains("feature2"));
    }

    @Test
    void getFeaturesByProfileName_thenThrowException() {
        when(openIdProfileRepository.findByProfileName("nonexistent"))
                .thenReturn(Collections.emptyList());

        EsignetException ex = assertThrows(EsignetException.class, () ->
                openIdProfileService.getFeaturesByProfileName("nonexistent"));
        assertTrue(ex.getMessage().contains("No features found for openid profile: nonexistent"));
    }

    @Test
    void getFeaturesByProfileName_whenNull_thenThrowException() {
        when(openIdProfileRepository.findByProfileName(null))
                .thenReturn(Collections.emptyList());

        EsignetException ex = assertThrows(EsignetException.class, () ->
                openIdProfileService.getFeaturesByProfileName(null));
        assertTrue(ex.getMessage().contains("No features found for openid profile: null"));
    }
}