package io.pinkspider.global.kafka.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import io.pinkspider.global.enums.AlimTalkTemplateCode;
import io.pinkspider.global.enums.AlimTalkType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// alim queue를 통해 들어오는 정보를 담는 객체
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlimTalkMessageDto {

    private AlimTalkType alimTalkType;
    // MMS title
    private String title;
    // 직접 백오피스에서 문구 작성시 담는 필드
    private String contents;
    // 멤버 정보가 없이 직적 전화번호를 통해 보내야 하는 경우에 사용. 그 외에는 멤버 아이디를 통해서 정보를 가져온다.
    private String callTo;

    private boolean userNameBinding;
    // 조회없이 넘겨받은 정보로 문자 발송
    private boolean direct;
    // 예약시간 설정
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    @JsonDeserialize(using = LocalDateTimeDeserializer.class)
    private LocalDateTime reservationTime;

    /**
     * ==================== 공통 kakao ====================
     */
    private String userId;

    private Long userGradeId;

    private String userName;
    // 차후를 위한 코드로 대출자가 여러번 받았을 경우를 생각해야 하기 때문
    private String loanTicketId;

    private String proposalId;
    // 투자금 지급을 위한 noteId
    private String noteId;

    private AlimTalkTemplateCode alimTalkTemplateCode;

    // ==================== 수익 리포트 데이터 ====================
    // 투자 성과 기간
    private String fundProfitPeriod;
    // 투자 수익 원리금
    private BigDecimal fundProfitTotal;
    // 투자 수익 원금
    private BigDecimal fundProfitPrincipal;
    // 투자 수익 이자
    private BigDecimal fundProfitInterest;
    // 연체 발생금액
    private BigDecimal fundTotalOverdueAmount;
    // 연체 발생금액
    private BigDecimal fundTotalOverdueInterest;
    // 연체 건수
    private Long fundOverdueCnt;
    // 상환 안내 스케쥴 아이디
    private Long fulfilmentScheduleId;
    private String marketTradingId;
    private Long marketNoteId;
    // sms 템플릿사용 변수
    private String smsTemplateName;
    private String smsValue1;
    private String smsValue2;
    private String smsValue3;
    private String smsValue4;
    private String smsValue5;
    private String smsValue6;
    // 투자 실행완료 변수
    private String fundStartDate;
    private Long fundCases;
    private BigDecimal fundAmount;
}
