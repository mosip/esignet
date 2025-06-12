/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package in.gov.uidai.entities;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
@Entity
@Table(name="kyc_auth", schema = "mockidentitysystem")
public class KycAuth {
    @Id
    @Column(name="kyc_token")
    private String kycToken;
    @Column(name="partner_specific_user_token")
    private String partnerSpecificUserToken;
    @Column(name="response_time")
    private LocalDateTime responseTime;
    @Column(name="transaction_id")
    private String transactionId;
    @Column(name="individual_id")
    private String individualId;

    public KycAuth(String s, String s1, LocalDateTime parse) {

    }


}


