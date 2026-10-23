package io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.DailyKpiValues;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** LUT-601: KST 날짜당 1행 KPI 스냅샷. 매일 00:40 스케줄러가 어제 행을 upsert 하고, rebuild API 로 소급·재계산한다. */
@Entity
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "daily_kpi_snapshot")
@Comment("일별 KPI 스냅샷 (LUT-601)")
public class DailyKpiSnapshot extends LocalDateTimeBaseEntity {

    @Id
    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "signups", nullable = false)
    private Integer signups;

    @Column(name = "withdrawals", nullable = false)
    private Integer withdrawals;

    @Column(name = "active_users", nullable = false)
    private Integer activeUsers;

    @Column(name = "dau", nullable = false)
    private Integer dau;

    @Column(name = "wau", nullable = false)
    private Integer wau;

    @Column(name = "mau", nullable = false)
    private Integer mau;

    @Column(name = "dau_web", nullable = false)
    private Integer dauWeb;

    @Column(name = "dau_ios", nullable = false)
    private Integer dauIos;

    @Column(name = "dau_android", nullable = false)
    private Integer dauAndroid;

    @Column(name = "attendance_dau", nullable = false)
    private Integer attendanceDau;

    @Column(name = "mission_completions", nullable = false)
    private Integer missionCompletions;

    @Column(name = "mission_active_users", nullable = false)
    private Integer missionActiveUsers;

    @Column(name = "new_subscriptions", nullable = false)
    private Integer newSubscriptions;

    @Column(name = "active_subscriptions", nullable = false)
    private Integer activeSubscriptions;

    @Column(name = "churned_subscriptions", nullable = false)
    private Integer churnedSubscriptions;

    @Column(name = "trial_starts", nullable = false)
    private Integer trialStarts;

    @Column(name = "trial_conversions", nullable = false)
    private Integer trialConversions;

    @Column(name = "paying_users", nullable = false)
    private Integer payingUsers;

    @Column(name = "revenue_krw", nullable = false, precision = 14, scale = 2)
    private BigDecimal revenueKrw;

    /** 통화별 매출 원본 JSON (예 {"KRW":4900.00,"USD":2.99}) — KRW 환산은 revenue_krw */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "revenue_by_currency")
    private String revenueByCurrency;

    public static DailyKpiSnapshot create(LocalDate date, DailyKpiValues v) {
        DailyKpiSnapshot s = new DailyKpiSnapshot();
        s.snapshotDate = date;
        s.apply(v);
        return s;
    }

    public void apply(DailyKpiValues v) {
        this.signups = v.signups();
        this.withdrawals = v.withdrawals();
        this.activeUsers = v.activeUsers();
        this.dau = v.dau();
        this.wau = v.wau();
        this.mau = v.mau();
        this.dauWeb = v.dauWeb();
        this.dauIos = v.dauIos();
        this.dauAndroid = v.dauAndroid();
        this.attendanceDau = v.attendanceDau();
        this.missionCompletions = v.missionCompletions();
        this.missionActiveUsers = v.missionActiveUsers();
        this.newSubscriptions = v.newSubscriptions();
        this.activeSubscriptions = v.activeSubscriptions();
        this.churnedSubscriptions = v.churnedSubscriptions();
        this.trialStarts = v.trialStarts();
        this.trialConversions = v.trialConversions();
        this.payingUsers = v.payingUsers();
        this.revenueKrw = v.revenueKrw();
        this.revenueByCurrency = v.revenueByCurrencyJson();
    }
}
