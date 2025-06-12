package in.gov.uidai.repositories;

import in.gov.uidai.entities.MockIdentity;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface IdentityRepository extends CrudRepository<MockIdentity, String>{

}
