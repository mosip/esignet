package io.mosip.esignet.core;

import io.mosip.esignet.api.dto.AuditDTO;
import io.mosip.esignet.api.util.Action;
import io.mosip.esignet.api.util.ActionStatus;
import io.mosip.esignet.core.util.LoggerAuditService;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class LoggerAuditServiceTest {
    
    @Mock
    private AuditDTO mockAuditDTO;

    @InjectMocks
    private LoggerAuditService loggerAuditService;

    @Test
    public void logAudit_withThrowable_thenPass() {
        Action action = Action.SEND_OTP;
        ActionStatus status = ActionStatus.ERROR;
        Throwable throwable = new RuntimeException("Test Exception");
        try {
            loggerAuditService.logAudit(action, status, mockAuditDTO, throwable);
            Assert.assertTrue(true);
        }catch(Exception e){
            Assert.fail();
        }
    }

    @Test(expected = NullPointerException.class)
    public void logAudit_withNullAction_throwsError() {
        ActionStatus status = ActionStatus.ERROR;
        Throwable throwable = new RuntimeException("Test Exception");
        loggerAuditService.logAudit(null, status, mockAuditDTO, throwable);
    }

    @Test
    public void logAudit_withDefaultStatus_thenPass() {
        Action action = Action.SEND_OTP;
        ActionStatus status = ActionStatus.SUCCESS;
        String username = "testUser";
        try {
            loggerAuditService.logAudit(username, action, status, mockAuditDTO, null);
            Assert.assertTrue(true);
        }catch(Exception e){
            Assert.fail();
        }
    }

    @Test
    public void logAudit_withErrorStatus_thenPass() {
        Action action = Action.SEND_OTP;
        ActionStatus status = ActionStatus.ERROR;
        String username = "testUser";
        try {
            loggerAuditService.logAudit(username, action, status, mockAuditDTO, null);
            Assert.assertTrue(true);
        }catch(Exception e){
            Assert.fail();
        }
    }

}
