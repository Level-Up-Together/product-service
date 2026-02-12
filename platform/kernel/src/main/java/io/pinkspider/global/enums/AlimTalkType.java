package io.pinkspider.global.enums;

import lombok.Getter;

public enum AlimTalkType {
    SMS,
    MMS,
    KAKAO;

    @Getter
    private String code;

//    @JsonCreator
//    public static AlimType fromAlimType(@JsonProperty("alimType") String name) {
//        return valueOf(name);
//    }

//    public static AlimType ofName(String code) {
//        return Arrays.stream(AlimType.values())
//            .filter(alimType -> alimType.getCode().equals(code))
//            .findAny().orElse(null);
//    }
}
