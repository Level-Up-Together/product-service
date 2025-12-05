package io.pinkspider.leveluptogethermvp.userservice.terms.domain.response;

public interface TermAgreementsByUserResponseDto {

    String getTermId();
    String getTermTitle();
    boolean getIsRequired();
    String getLatestVersionId();
    String getVersion();
    boolean getIsAgreed();
    String getAgreedAt();
}
