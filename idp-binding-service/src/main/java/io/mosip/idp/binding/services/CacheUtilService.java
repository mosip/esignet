/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.binding.services;

import io.mosip.idp.binding.dto.BindingTransaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class CacheUtilService {

    private static final String WALLET_BINDING_CACHE = "walletbinding";

    @Autowired
    CacheManager cacheManager;

    @Cacheable(value = WALLET_BINDING_CACHE, key = "#transactionId")
    public BindingTransaction setTransaction(String transactionId, BindingTransaction transaction) {
        return transaction;
    }

    @CacheEvict(value = WALLET_BINDING_CACHE, key = "#transactionId")
    public BindingTransaction getTransaction(String transactionId) {
        return cacheManager.getCache(WALLET_BINDING_CACHE).get(transactionId, BindingTransaction.class);
    }

}
