package io.pinkspider.leveluptogethermvp.notificationservice.infrastructure;

import io.pinkspider.leveluptogethermvp.notificationservice.domain.entity.AdminPushCampaign;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** LUT-508: 관리자 푸시 발송 이력 */
public interface AdminPushCampaignRepository extends JpaRepository<AdminPushCampaign, Long> {

    Page<AdminPushCampaign> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);
}
