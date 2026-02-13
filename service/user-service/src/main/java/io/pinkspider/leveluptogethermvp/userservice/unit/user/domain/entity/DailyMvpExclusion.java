package io.pinkspider.leveluptogethermvp.userservice.unit.user.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "daily_mvp_exclusion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyMvpExclusion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mvp_date", nullable = false)
    private LocalDate mvpDate;

    @Column(name = "user_id", nullable = false, length = 36)
    private String userId;

    @Column(name = "reason", nullable = false, length = 500)
    private String reason;

    @Column(name = "admin_id")
    private Long adminId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
