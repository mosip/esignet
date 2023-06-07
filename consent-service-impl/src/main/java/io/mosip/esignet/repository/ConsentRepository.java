package io.mosip.esignet.repository;

import io.mosip.esignet.entity.ConsentDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;
@Repository
public interface ConsentRepository extends JpaRepository<ConsentDetail, UUID> {
      Optional<ConsentDetail> findFirstByClientIdAndPsuTokenOrderByCreatedtimesDesc(String clientId, String psuToken);
}
