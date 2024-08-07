/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.core.spi;

import io.mosip.esignet.core.dto.PublicKeyRegistry;
import java.util.Optional;

public interface PublicKeyRegistryService {

    Optional<PublicKeyRegistry> findLatestPublicKeyByPsuTokenAndAuthFactor(String psuToken, String authFactor);
    Optional<PublicKeyRegistry> findFirstByIdHashAndThumbprintAndExpiredtimes(String idHash, String thumbPrint);
}
