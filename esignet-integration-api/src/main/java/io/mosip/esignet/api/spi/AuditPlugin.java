/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.api.spi;

import io.mosip.esignet.api.util.Action;
import io.mosip.esignet.api.util.ActionStatus;
import io.mosip.esignet.api.dto.AuditDTO;

public interface AuditPlugin {

    /**
     + Plugin method to audit all the actions in e-Signet service.
     +
     +  @param action Action to audit @{@link Action}
     +  @param actionStatus Action status to audit @{@link ActionStatus}
     +  @param audit @{@link AuditDTO} during this action
     +  @param t Any error / exception occurred during this action, null if no errors / exception found.
     */
    void logAudit(Action action, ActionStatus status, AuditDTO audit, Throwable t);

    /**
    + Plugin method to audit all the actions in e-Signet service.
    +
    +  @param username Session username for audit
    +  @param action Action to audit @{@link Action}
    +  @param actionStatus Action status to audit @{@link ActionStatus}
    +  @param audit @{@link AuditDTO} during this action
    +  @param t Any error / exception occurred during this action, null if no errors / exception found.
    */
	void logAudit(String username, Action action, ActionStatus status, AuditDTO audit, Throwable t);
}
