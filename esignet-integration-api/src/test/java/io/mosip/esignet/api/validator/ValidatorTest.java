package io.mosip.esignet.api.validator;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.util.ReflectionTestUtils;

import javax.validation.ConstraintValidatorContext;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(MockitoJUnitRunner.class)
public class ValidatorTest {

    @InjectMocks
    private PurposeValidator purposeValidator;

    @Mock
    private ConstraintValidatorContext constraintValidatorContext;

    @Before
    public void setUp() {
        ReflectionTestUtils.setField(purposeValidator, "minLength", 3);
        ReflectionTestUtils.setField(purposeValidator, "maxLength", 300);
    }

    @Test
    public void testIsValid_WithValidPurpose_thenPass() {
        String purpose = "Purpose";
        boolean isValid = purposeValidator.isValid(purpose, constraintValidatorContext);
        assertTrue(isValid);
    }

    @Test
    public void testIsValid_WithPurposeWithInvalidLength_thenFail() {
        String purpose = "In";
        boolean isValid = purposeValidator.isValid(purpose, constraintValidatorContext);
        assertFalse(isValid);
    }

    @Test
    public void testIsValid_WithNullPurpose_theFail() {
        String purpose = null;
        boolean isValid = purposeValidator.isValid(purpose, constraintValidatorContext);
        assertFalse(isValid);
    }

    @Test
    public void testIsValid_WithEmptyPurpose_thenFail() {
        String purpose = "";
        boolean isValid = purposeValidator.isValid(purpose, constraintValidatorContext);
        assertFalse(isValid);
    }
}
