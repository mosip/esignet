/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.core.dto;

import lombok.Data;

import java.util.List;

@Data
public class DiscoveryResponse {

    private String issuer;
    private String authorization_endpoint;
    private String token_endpoint;
    private List<String> token_endpoint_auth_methods_supported;
    private List<String> token_endpoint_auth_signing_alg_values_supported;
    private String userinfo_endpoint;
    private String check_session_iframe;
    private String end_session_endpoint;
    private String jwks_uri;
    private String registration_endpoint;
    private List<String> scopes_supported;
    private List<String> response_types_supported;
    private List<String> acr_values_supported;
    private List<String> subject_types_supported;
    private List<String> userinfo_signing_alg_values_supported;
    private List<String> userinfo_encryption_alg_values_supported;
    private List<String> userinfo_encryption_enc_values_supported;
    private List<String> id_token_signing_alg_values_supported;
    private List<String> id_token_encryption_alg_values_supported;
    private List<String> id_token_encryption_enc_values_supported;
    private List<String> request_object_signing_alg_values_supported;
    private List<String> display_values_supported;
    private List<String> claim_types_supported;
    private List<String> claims_supported;
    private boolean claims_parameter_supported;
    private String service_documentation;
    private List<String> ui_locales_supported;
}
