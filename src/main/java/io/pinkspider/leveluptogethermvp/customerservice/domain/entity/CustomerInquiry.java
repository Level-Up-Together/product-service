package io.pinkspider.leveluptogethermvp.customerservice.domain.entity;

import io.pinkspider.leveluptogethermvp.customerservice.domain.enums.InquiryStatus;
import io.pinkspider.leveluptogethermvp.customerservice.domain.enums.InquiryType;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "customer_inquiry")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class CustomerInquiry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "user_nickname", length = 100)
    private String userNickname;

    @Column(name = "user_email", length = 255)
    private String userEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "inquiry_type", nullable = false, length = 50)
    private InquiryType inquiryType;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    @Builder.Default
    private InquiryStatus status = InquiryStatus.PENDING;

    @Column(name = "admin_id")
    private Long adminId;

    @Column(name = "admin_memo", columnDefinition = "TEXT")
    private String adminMemo;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "modified_at", nullable = false)
    private LocalDateTime modifiedAt;

    @OneToMany(mappedBy = "inquiry", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CustomerInquiryReply> replies = new ArrayList<>();

    public void updateStatus(InquiryStatus status) {
        this.status = status;
    }

    public void assignAdmin(Long adminId) {
        this.adminId = adminId;
        if (this.status == InquiryStatus.PENDING) {
            this.status = InquiryStatus.IN_PROGRESS;
        }
    }

    public void updateAdminMemo(String memo) {
        this.adminMemo = memo;
    }
}
