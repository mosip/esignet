package io.mosip.idp.services;

import io.mosip.idp.core.dto.AuditDTO;
import io.mosip.idp.core.spi.AuditWrapper;
import io.mosip.idp.core.util.Action;
import io.mosip.idp.core.util.ActionStatus;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import javax.validation.constraints.NotNull;

@ConditionalOnProperty(value = "mosip.idp.audit.wrapper.impl", havingValue = "LoggerAuditService")
@Component
@Slf4j
public class LoggerAuditService implements AuditWrapper {

    @Async
    @Override
    public void logAudit(@NotNull Action action, @NotNull ActionStatus status, @NotNull AuditDTO auditDTO, Throwable t) {
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
                    log.info(action.name());
            }
        } finally {
            MDC.clear();
        }
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
}
