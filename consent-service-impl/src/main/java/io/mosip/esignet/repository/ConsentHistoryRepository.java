/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.repository;



import io.mosip.esignet.entity.ConsentHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsentHistoryRepository extends JpaRepository<ConsentHistory, String>{

}