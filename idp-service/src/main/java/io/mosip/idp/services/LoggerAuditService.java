package io.mosip.idp.services;

import io.mosip.idp.core.dto.AuditableIdPTransaction;
import io.mosip.idp.core.spi.AuditWrapper;
import io.mosip.idp.core.util.IdPAction;
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
    public void logAudit(@NotNull IdPAction action, @NotNull AuditableIdPTransaction transaction, Throwable t) {
        addTransactionDetailsToMDC(transaction);
        try {
            if(t != null) {
                log.error(action.name(), t);
                return;
            }

            if(action.getState().equalsIgnoreCase("failed")) {
                log.error(action.name());
                return;
            }

            log.info(action.name());
        } finally {
            MDC.clear();
        }
    }

    private void addTransactionDetailsToMDC(AuditableIdPTransaction transaction) {
        if(transaction != null) {
            MDC.put("transactionId", transaction.getTransactionId());
            MDC.put("clientId", transaction.getClientId());
            MDC.put("relyingPartyId", transaction.getRelyingPartyId());
            MDC.put("state", transaction.getState());
            MDC.put("authCodeGenerated", String.valueOf(transaction.getCodeHash()!=null));
            MDC.put("tokenGenerated", String.valueOf(transaction.getAHash()!=null));
        }
    }
}
