package io.pinkspider.leveluptogethermvp.userservice.terms.domain.response;

public interface RecentTermsResponseDto {

    String getTermId();
    String getTermTitle();
    String getCode();
    String getType();
    boolean getIsRequired();
    String getVersionId();
    String getVersion();
    String getCreatedAt();
    String getContent();
}
