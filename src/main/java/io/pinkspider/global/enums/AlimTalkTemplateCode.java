package io.pinkspider.global.enums;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

// LG CNS에 등록된 메세지 템플릿 아이디 정보
// TODO DB에 보관하고 읽어오기
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public enum AlimTalkTemplateCode {

    // 회원 관련 (대출자/투자자 등)
    USER_JOIN("회원가입 안내", "userJoin", "UserJoin"),
    USER_JOIN_INVESTOR("투자 회원가입 안내", "userJoinInvestor", "UserJoin_"),
    USER_GRADE_REQUEST("투자자 유형 전환 신청", "userGradeRequest", "UserGradeRequest"),
    USER_GRADE_REQUEST2("[관리자]투자자 유형 전환 신청", "userGradeRequestForAdmin", "UserGradeRequest2"),
    USER_GRADE("투자자 유형 전환 완료", "userGrade", "UserGrade_"),
    USER_WITHDRAWAL("회원 탈퇴", "userWithdrawal", "UsershipWithdrawal"),

    // 대출 관련
    LOAN_AUDIT_FAIL("대출 심사 탈락 안내", "loanAuditFail", "LoanFail"),
    LOAN_PARTNERSHIP("제휴대출 신청 안내", "loanPartnership", "LoanPartnership_"),
    LOAN_REQUEST("대출 서류 제출 완료 안내", "loanRequest", "LoanRequest"),
    LOAN_RECRUIT("대출 모집 실행 안내", "loanRecruit", "LoanRecruit_"),
    LOAN_CANCEL("대출 모집 취소 안내", "loanCancel", "LoanCancel"),
    LOAN_REFUSE("대출 불가 안내", "loanRefuse", "RefuseLoan"),
    LOAN_CONTRACT_STAND("대출 모집 결과 안내", "loanContractStand", "LoanContractStand"),
    LOAN_CONTRACT_COMPLETE("대출계약서작성 완료 안내", "loanContractComplete", "LoanContractCp"),
    LOAN_EXECUTION("신용대출 실행 안내", "loanExecution", "LoanExecution_"),
    LOAN_PAY_NOTICE("대출금 납부 안내(5영업일 전)", "loanPaymentNotice", "LoanPaynotice_"),
    LOAN_PAY_DAY("대출금 납부 안내(당일)", "loanPaymentNoticeDay", "LoanPayDay_"),
    LOAN_PAYMENT("대출금 납입 안내", "loanPayment", "LoanPayment_"),
    LOAN_EARLY_PAYMENT("중도일시상환 신청", "loanEarlyPayment", "LoanEarlyPayment"),
    LOAN_EARLY_PAYMENT_CANCEL("중도일시상환 신청취소", "loanEarlyPaymentCancel", "LoanPayCancel_"),
    LOAN_PREPAYMENT("대출금 완납 안내(중도일시상환)", "loanPrepayment", "LoanPrepayment"),
    LOAN_PAYMENT_FULL("대출금 완납 안내", "loanPaymentFull", "LaonPaymentFull"),

    // 투자 관련
    FUND_CANCEL_FOR_INVESTOR("투자취소(투자자)", "fundCancelForInvestor", "FundCancelInvestor"),
    FUND_CANCEL_FOR_BORROWER("투자 취소 안내(대출자 취소)", "fundCancelForBorrower", "FundCancel_"),
    FUND_CANCEL_FOR_ADMIN("투자 취소 안내(관리자 취소)", "fundCancelForAdmin", "FundCancelAdmin_"),
    FUND_FAIL("투자 모집 실패", "fundFail", "FundFail_"),
    FUND_COMPLETE("투자 실행 완료", "fundComplete", "FundComplete_"),
    FUND_RETURN("투자 성과 안내", "fundReturn", "FundReturn_"),
    FUND_OVERDUE("투자 연체발생 안내", "fundOverdue", "FundOverdue_"),

    // 마켓 관련
    NOTE_PURCHASING("원리금수취권 구매", "notePurchasing", "NotePurchasing_"),
    NOTE_SELLING("원리금수취권 판매", "noteSelling", "NoteSelling_"),
    NOTE_SELLING_CANCEL("원리금수취권 판매 취소", "noteSellingCancel", "NoteSellingCancel_");

    @Getter
    private String description;

    @Getter
    private String methodName;

    @Getter
    private String templateId;

}
