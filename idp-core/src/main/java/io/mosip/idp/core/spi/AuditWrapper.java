/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.core.spi;

import io.mosip.idp.core.dto.IdPTransaction;
import io.mosip.idp.core.util.IdPAction;

public interface AuditWrapper {

    /**
     + Wrapper method to audit all the actions in Idp service.
     +
     +  @param action Action to audit @{@link IdPAction}
     +  @param transaction @{@link IdPTransaction} during this action
     +  @param message Describing the action
     +  @param t Any error / exception occurred during this action, null if no errors / exception found.
     */
    void logAudit(IdPAction action, IdPTransaction transaction, String message, Throwable t);
}
