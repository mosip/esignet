/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.core.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.concurrent.ConcurrentHashMap;

@Data
public class ApiRateLimit implements Serializable {

    ConcurrentHashMap<Integer, Integer> count = new ConcurrentHashMap<>();
    ConcurrentHashMap<Integer, Long> lastInvocation = new ConcurrentHashMap<>();

    public void increment(int apiCode) {
        count.compute(apiCode, (k, v) -> (v == null) ? 1 : v + 1);
        lastInvocation.compute(apiCode, (k, v) -> (v == null) ? 0 : v);
    }

    public void updateLastInvocation(int apiCode) {
        lastInvocation.compute(apiCode, (k, v) -> System.currentTimeMillis()/1000 );
    }
}
