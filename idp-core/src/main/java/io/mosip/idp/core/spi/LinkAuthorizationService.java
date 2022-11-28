/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.core.spi;

import io.mosip.idp.core.dto.*;
import io.mosip.idp.core.exception.IdPException;
import org.springframework.web.context.request.async.DeferredResult;

public interface LinkAuthorizationService {

    /**
     * Validates the provided transactionId and generates a link-code with a ttl.
     * @param linkCodeRequest
     * @return
     */
    LinkCodeResponse generateLinkCode(LinkCodeRequest linkCodeRequest) throws IdPException;;

    /**
     * Starts a linked-transaction for the provided valid and active link-code.
     * The started linked-transaction is identified with linked-transaction-id
     * @param linkTransactionRequest
     * @return
     */
    LinkTransactionResponse linkTransaction(LinkTransactionRequest linkTransactionRequest) throws IdPException;;

    /**
     * Returns the status of linked-transaction if any.
     * @param linkStatusRequest
     * @return
     */
    void getLinkStatus(DeferredResult deferredResult, LinkStatusRequest linkStatusRequest) throws IdPException;;

    /**
     * Returns the authentication and consent status of the linked-transaction.
     * @param linkAuthCodeRequest
     * @return
     */
    void getLinkAuthCodeStatus(DeferredResult deferredResult, LinkAuthCodeRequest linkAuthCodeRequest) throws IdPException;;

}
