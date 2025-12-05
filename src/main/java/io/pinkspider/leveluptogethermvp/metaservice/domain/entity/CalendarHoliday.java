package io.pinkspider.leveluptogethermvp.metaservice.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Comment;

@Entity
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Comment("휴일")
@Table(name = "calendar_holiday")
public class CalendarHoliday {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Comment("휴일 ID")
    @Column(name = "id", nullable = false)
    private Integer id;

    @NotNull
    @Comment("연")
    @Column(name = "years", nullable = false)
    private Integer years;

    @Size(max = 4)
    @Comment("월일")
    @Column(name = "mmdd", length = 4)
    private String mmdd;

    @Comment("휴일여부")
    @Column(name = "is_holiday")
    private Boolean isHoliday;

    @Comment("연간 휴일 수")
    @Column(name = "year_count")
    private Integer yearCount;

    @Transient
    private String business1Day;

    @Transient
    private String business2Day;

    @Transient
    private String business3Day;

    @Transient
    private String business4Day;

    @Transient
    private String business5Day;

    @Transient
    private String business6Day;

    @Transient
    private String business7Day;

}
