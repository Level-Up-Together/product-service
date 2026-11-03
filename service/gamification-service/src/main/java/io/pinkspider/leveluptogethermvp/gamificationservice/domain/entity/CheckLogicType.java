package io.pinkspider.leveluptogethermvp.gamificationservice.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.CheckLogicComparisonOperator;
import io.pinkspider.leveluptogethermvp.gamificationservice.domain.enums.CheckLogicDataSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Comment;

@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(
        name = "check_logic_type",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_check_logic_type_code",
                        columnNames = {"code"}),
        indexes = {
            @Index(name = "idx_check_logic_type_code", columnList = "code"),
            @Index(name = "idx_check_logic_type_data_source", columnList = "data_source"),
            @Index(name = "idx_check_logic_type_active", columnList = "is_active"),
            @Index(name = "idx_check_logic_type_sort", columnList = "sort_order")
        })
@Comment("업적 체크 로직 유형")
public class CheckLogicType extends LocalDateTimeBaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    @Comment("체크 로직 유형 ID")
    private Long id;

    @NotNull
    @Column(name = "code", nullable = false, length = 50)
    @Comment("체크 로직 코드 (UNIQUE)")
    private String code;

    @NotNull
    @Column(name = "name", nullable = false, length = 100)
    @Comment("체크 로직 이름")
    private String name;

    @Column(name = "description", length = 500)
    @Comment("설명")
    private String description;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "data_source", nullable = false, length = 50)
    @Comment("데이터 소스")
    private CheckLogicDataSource dataSource;

    @NotNull
    @Column(name = "data_field", nullable = false, length = 100)
    @Comment("비교할 데이터 필드명")
    private String dataField;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "comparison_operator", nullable = false, length = 20)
    @Comment("비교 연산자")
    @Builder.Default
    private CheckLogicComparisonOperator comparisonOperator = CheckLogicComparisonOperator.GTE;

    @Column(name = "config_json", columnDefinition = "TEXT")
    @Comment("추가 설정 JSON (확장용)")
    private String configJson;

    @NotNull
    @Column(name = "sort_order", nullable = false)
    @Comment("정렬 순서")
    @Builder.Default
    private Integer sortOrder = 0;

    @NotNull
    @Column(name = "is_active", nullable = false)
    @Comment("활성화 여부")
    @Builder.Default
    private Boolean isActive = true;
}
