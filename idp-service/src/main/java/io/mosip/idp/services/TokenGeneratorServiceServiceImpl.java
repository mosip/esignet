/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TokenGeneratorServiceServiceImpl implements io.mosip.idp.core.spi.TokenGeneratorService {

    private static final Logger logger = LoggerFactory.getLogger(TokenGeneratorServiceServiceImpl.class);

    @Override
    public String getIDToken() {
        return null;
    }

    @Override
    public String getAccessToken() {
        return null;
    }
}
