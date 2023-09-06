/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet;

import io.mosip.esignet.core.dto.vci.ParsedAccessToken;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.web.context.annotation.RequestScope;

@SpringBootTest
public class EsignetServiceApplicationTests {

	@Test
	public void test() {
		EsignetServiceApplication.main(new String[] {});
		Assert.assertNotNull(EsignetServiceApplication.class);
	}

	@Bean
	@RequestScope
	public ParsedAccessToken parsedAccessToken() {
		return new ParsedAccessToken();
	}

}
