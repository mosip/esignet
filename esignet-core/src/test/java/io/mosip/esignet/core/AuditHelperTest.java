package io.mosip.esignet.core;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import io.mosip.esignet.api.dto.AuditDTO;
import io.mosip.esignet.core.dto.IdPTransaction;
import io.mosip.esignet.core.util.AuditHelper;

@RunWith(MockitoJUnitRunner.class)
public class AuditHelperTest {
	
	AuditHelper auditHelper = new AuditHelper();

	@Test
	public void test_buildAuditDto_withClientID() {
		AuditDTO auditDTO = AuditHelper.buildAuditDto("test-client-id");
		Assert.assertSame(auditDTO.getClientId(), "test-client-id");
	}
	
	@Test
	public void test_buildAuditDto_withTransaction() {
		IdPTransaction transaction = new IdPTransaction();
		transaction.setLinkedTransactionId("89019103");
		transaction.setAuthTransactionId("90910310");
		transaction.setRelyingPartyId("test-relyingparty-id");
		transaction.setClientId("test-client-id");
		AuditDTO auditDTO = AuditHelper.buildAuditDto("1234567890", transaction);
		Assert.assertSame(auditDTO.getLinkedTransactionId(), "89019103");
		Assert.assertSame(auditDTO.getAuthTransactionId(), "90910310");
		Assert.assertSame(auditDTO.getRelyingPartyId(), "test-relyingparty-id");
		Assert.assertSame(auditDTO.getClientId(), "test-client-id");
	}
}
