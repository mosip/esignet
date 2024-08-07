/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.repository;

import io.mosip.esignet.entity.ConsentDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;
@Repository
public interface ConsentRepository extends JpaRepository<ConsentDetail, UUID> {
      boolean existsByClientIdAndPsuToken(String clientId, String psuToken);
      Optional<ConsentDetail> findByClientIdAndPsuToken(String clientId, String psuToken);
      void deleteByClientIdAndPsuToken(String clientId, String psuToken);

}
