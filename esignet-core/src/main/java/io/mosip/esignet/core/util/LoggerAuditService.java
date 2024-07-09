package io.mosip.esignet.core.util;

import io.mosip.esignet.api.dto.AuditDTO;
import io.mosip.esignet.api.spi.AuditPlugin;
import io.mosip.esignet.api.util.Action;
import io.mosip.esignet.api.util.ActionStatus;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import javax.validation.constraints.NotNull;

@ConditionalOnProperty(value = "mosip.esignet.integration.audit-plugin", havingValue = "LoggerAuditService")
@Component
@Slf4j
public class LoggerAuditService implements AuditPlugin {

    @Async
    @Override
    public void logAudit(@NotNull Action action, @NotNull ActionStatus status, @NotNull AuditDTO auditDTO, Throwable t) {
        audit(null, action, status, auditDTO, t);
    }

    @Async
    @Override
    public void logAudit(String username, Action action, ActionStatus status, AuditDTO auditDTO, Throwable t) {
        audit(username, action, status, auditDTO, t);
    }

    private void addAuditDetailsToMDC(AuditDTO auditDTO) {
        if(auditDTO != null) {
            MDC.put("transactionId", auditDTO.getTransactionId());
            MDC.put("clientId", auditDTO.getClientId());
            MDC.put("relyingPartyId", auditDTO.getRelyingPartyId());
            MDC.put("state", auditDTO.getState());
            MDC.put("authCodeHash", auditDTO.getCodeHash());
            MDC.put("accessTokenHash", auditDTO.getAccessTokenHash());
        }
    }

    public void audit(String username, Action action, ActionStatus status, AuditDTO auditDTO, Throwable t) {
        addAuditDetailsToMDC(auditDTO);
        try {
            if(t != null) {
                log.error(action.name(), t);
                return;
            }

            switch (status) {
                case ERROR:
                    log.error(action.name());
                    break;
                default:
                    log.info(username != null ? "Sessionuser: " +username+ "with action: " +action.name() : action.name());
            }
        } finally {
            MDC.clear();
        }
    }
}
