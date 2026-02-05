/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.services;

/**
 * Enum representing the type of the input KYC data from KYC-exchange.
 * Used for detecting the format of encryptedKyc response.
 */
public enum KycDataFormat {
    JWE,
    JWS,
    PLAIN_JSON
}

