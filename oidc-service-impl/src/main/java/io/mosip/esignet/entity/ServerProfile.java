/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;

@Data
@Entity
@Table(name = "server_profile")
@NoArgsConstructor
@AllArgsConstructor
@IdClass(ServerProfileId.class)
public class ServerProfile {
    @Id
    @Column(name = "profile_name", length = 100, nullable = false)
    private String profileName;

    @Id
    @Column(name = "feature", length = 100, nullable = false)
    private String feature;

    @Column(name = "additional_config_key", length = 200, nullable = false)
    private String additionalConfigKey;

}
