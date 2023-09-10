/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.core;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.jwt.Jwt;

import io.mosip.esignet.api.dto.AuditDTO;
import io.mosip.esignet.core.dto.OIDCTransaction;
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
		OIDCTransaction transaction = new OIDCTransaction();
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
	
	@Test
	public void test_getClaimValue_withValidDetails() {
		SecurityContext context = new SecurityContextImpl();
		context.setAuthentication(getTestAuthentication(true, true));
		String claimValue = AuditHelper.getClaimValue(context, "fullName");
		Assert.assertEquals(claimValue, "Test Name");
	}
	
	@Test
	public void test_getClaimValue_withInValidAuthentication() {
		SecurityContext context = new SecurityContextImpl();
		context.setAuthentication(null);
		String claimValue = AuditHelper.getClaimValue(context, "fullName");
		Assert.assertNull(claimValue);
	}
	
	@Test
	public void test_getClaimValue_withNullPrincipal() {
		SecurityContext context = new SecurityContextImpl();
		context.setAuthentication(getTestAuthentication(false, false));
		String claimValue = AuditHelper.getClaimValue(context, "fullName");
		Assert.assertNull(claimValue);
	}
	
	@Test
	public void test_getClaimValue_withInValidPrincipal() {
		SecurityContext context = new SecurityContextImpl();
		context.setAuthentication(getTestAuthentication(true, false));
		String claimValue = AuditHelper.getClaimValue(context, "fullName");
		Assert.assertNull(claimValue);
	}

	private Authentication getTestAuthentication(boolean isPrincipalRequired, boolean isJwtRequired) {
		return new Authentication() {
			private static final long serialVersionUID = 1L;
			@Override
			public String getName() {
				return null;
			}			
			@Override
			public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {				
			}			
			@Override
			public boolean isAuthenticated() {
				return false;
			}		
			@Override
			public Object getPrincipal() {
				if (!isPrincipalRequired) {
					return null;
				}
				if (!isJwtRequired) {
					return "dummy principal";
				}
				Map<String, Object> claims = new HashMap<>();
				claims.put("fullName", "Test Name");
				Jwt jwt = new Jwt("test-token", Instant.EPOCH, Instant.now(), claims, claims);
				return jwt;
			}			
			@Override
			public Object getDetails() {
				return null;
			}			
			@Override
			public Object getCredentials() {
				return null;
			}			
			@Override
			public Collection<? extends GrantedAuthority> getAuthorities() {
				return null;
			}
		};
	}
}
