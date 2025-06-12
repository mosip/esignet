package in.gov.uidai.repositories;

import in.gov.uidai.entities.KycAuth;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
@Repository
public interface AuthRepository extends JpaRepository<KycAuth,String> {
    Optional<KycAuth> findByKycTokenAndTransactionIdAndIndividualId(String kycToken, String transactionId, String individualId);
}
