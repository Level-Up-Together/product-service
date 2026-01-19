package io.pinkspider.global.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommonCodeDto {

    private String id;
    private String codeName;
    private String codeTitle;
    private String codeTitleEn;
    private String codeTitleAr;
    private String description;
    private String parentId;

    /**
     * 로케일에 따른 제목 반환
     * @param locale 언어 코드 (ko, en, ar)
     * @return 해당 언어의 제목, 없으면 한국어 제목
     */
    public String getLocalizedTitle(String locale) {
        if (locale == null) {
            return codeTitle;
        }
        return switch (locale.toLowerCase()) {
            case "en" -> codeTitleEn != null ? codeTitleEn : codeTitle;
            case "ar" -> codeTitleAr != null ? codeTitleAr : codeTitle;
            default -> codeTitle;
        };
    }
}
