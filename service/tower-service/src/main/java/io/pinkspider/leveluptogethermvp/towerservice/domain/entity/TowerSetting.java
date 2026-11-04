package io.pinkspider.leveluptogethermvp.towerservice.domain.entity;

import io.pinkspider.global.domain.auditentity.LocalDateTimeBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

/** LUT-635: 탑 공통 설정(키-값). 층 설정이 아닌 탑 전체 값 — 동시 접속 상한(대기열 LUT-701 기준) 등. */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Table(name = "tower_setting")
@Comment("레벨업 탑 공통 설정")
public class TowerSetting extends LocalDateTimeBaseEntity {
    @Id
    @Column(name = "setting_key", nullable = false, length = 50)
    private String settingKey;

    @Column(name = "setting_value", nullable = false, length = 500)
    private String settingValue;

    public void update(String value) {
        this.settingValue = value;
    }
}
