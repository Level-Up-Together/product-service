package io.pinkspider.leveluptogethermvp.statsservice.kpi.domain.dto.source;

/** LUT-790: 미션 카테고리 이름 (meta_db {@code mission_category}) — 영어 이름이 없으면 null */
public record MissionCategoryName(String name, String nameEn) {}
