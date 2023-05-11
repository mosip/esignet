package io.mosip.esignet.household.integration;
import io.mosip.esignet.api.dto.AuditDTO;
import io.mosip.esignet.api.util.Action;
import io.mosip.esignet.api.util.ActionStatus;
import io.mosip.esignet.household.integration.service.LoggerAuditService;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.junit.MockitoJUnitRunner;
import static org.junit.Assert.assertThrows;

@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class LoggerAuditServiceTest {

    @InjectMocks
    private LoggerAuditService loggerAuditService;

    @Test
    public void logAudit_WithValidDetails_ThenPass() {
        Action action = Action.AUTHENTICATE;
        ActionStatus status = ActionStatus.SUCCESS;
        AuditDTO auditDTO = new AuditDTO();
        try {
            loggerAuditService.logAudit( action, status, auditDTO, null);
            Assert.assertTrue(true);
        }catch (Exception e) {
            Assert.fail();
        }
    }

    @Test
    public void logAudit_WithNullAction_ThenFail() {
        Action action = null;
        ActionStatus status = ActionStatus.SUCCESS;
        AuditDTO auditDTO = new AuditDTO();
        assertThrows(NullPointerException.class, () -> loggerAuditService.logAudit(action, status, auditDTO, null));
    }

    @Test
    public void logAudit_withNullStatus() {
        Action action = Action.AUTHENTICATE;
        ActionStatus status = null;
        AuditDTO auditDTO = new AuditDTO();
        assertThrows(NullPointerException.class, () -> loggerAuditService.logAudit(action, status, auditDTO, null));
    }

    @Test
    @Ignore
    public void logAudit_WithNullAuditDTO_ThenFail() {
        Action action = Action.AUTHENTICATE;
        ActionStatus status = ActionStatus.SUCCESS;
        AuditDTO auditDTO = null;
        assertThrows(NullPointerException.class, () -> loggerAuditService.logAudit(action, status, auditDTO, null));
    }

    @Test
    public void logAuditWithUsername_WithValidDetails_ThenPass() {
        String username = "abc";
        Action action = Action.AUTHENTICATE;
        ActionStatus status = ActionStatus.SUCCESS;
        AuditDTO auditDTO = new AuditDTO();
        try {
            loggerAuditService.logAudit(username, action, status, auditDTO, null);
            Assert.assertTrue(true);
        }catch (Exception e) {
            Assert.fail();
        }
    }
}