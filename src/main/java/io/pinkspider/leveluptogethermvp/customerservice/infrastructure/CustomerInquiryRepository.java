package io.pinkspider.leveluptogethermvp.customerservice.infrastructure;

import io.pinkspider.leveluptogethermvp.customerservice.domain.entity.CustomerInquiry;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerInquiryRepository extends JpaRepository<CustomerInquiry, Long> {

    @Query("SELECT ci FROM CustomerInquiry ci WHERE ci.userId = :userId ORDER BY ci.createdAt DESC")
    List<CustomerInquiry> findByUserIdOrderByCreatedAtDesc(@Param("userId") String userId);

    @Query("SELECT ci FROM CustomerInquiry ci WHERE ci.userId = :userId ORDER BY ci.createdAt DESC")
    Page<CustomerInquiry> findByUserIdOrderByCreatedAtDesc(@Param("userId") String userId, Pageable pageable);

    @Query("SELECT ci FROM CustomerInquiry ci LEFT JOIN FETCH ci.replies WHERE ci.id = :id AND ci.userId = :userId")
    CustomerInquiry findByIdAndUserIdWithReplies(@Param("id") Long id, @Param("userId") String userId);
}
