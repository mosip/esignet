package io.mosip.idp.repositories;

import io.mosip.idp.domain.ClientDetail;
import org.springframework.data.repository.CrudRepository;

import java.util.Optional;

public interface ClientDetailRepository extends CrudRepository<ClientDetail, String> {

    /**
     * case-sensitive query to fetch client with clientId and status
     * @param clientId
     * @param status
     * @return
     */
    Optional<ClientDetail> findByIdAndStatus(String clientId, String status);

}
