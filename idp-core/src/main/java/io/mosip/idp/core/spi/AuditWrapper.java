/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.core.spi;

import io.mosip.idp.core.dto.AuditDTO;
import io.mosip.idp.core.util.Action;
import io.mosip.idp.core.util.ActionStatus;

public interface AuditWrapper {

    /**
     + Wrapper method to audit all the actions in Idp service.
     +
     +  @param action Action to audit @{@link Action}
     +  @param audit @{@link AuditDTO} during this action
     +  @param t Any error / exception occurred during this action, null if no errors / exception found.
     */
    void logAudit(Action action, ActionStatus status, AuditDTO audit, Throwable t);
}
