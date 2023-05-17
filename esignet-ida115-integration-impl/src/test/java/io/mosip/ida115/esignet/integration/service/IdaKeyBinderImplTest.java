package io.mosip.ida115.esignet.integration.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import io.mosip.esignet.api.exception.KeyBindingException;
import io.mosip.esignet.api.exception.SendOtpException;

@Ignore
@RunWith(MockitoJUnitRunner.class)
public class IdaKeyBinderImplTest {

    @InjectMocks
    private Ida115KeyBinderImpl idaKeyBinderImpl;


    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void sendBindingOtp_thenFail() {
        Map<String, String> headers = new HashMap<>();
        try {
            idaKeyBinderImpl.sendBindingOtp("individualId", Arrays.asList("email"), headers);
            Assert.fail();
        } catch (SendOtpException e) {
            Assert.assertEquals("not_implemented", e.getErrorCode());
        }
    }


    @Test
    public void doKeyBinding_thenFail() {
        Map<String, String> headers = new HashMap<>();
        try {
            idaKeyBinderImpl.doKeyBinding("individualId", new ArrayList<>(), new HashMap<>(),
                    "WLA", headers);
            Assert.fail();
        } catch (KeyBindingException e) {
            Assert.assertEquals("not_implemented", e.getErrorCode());
        }
    }
}
