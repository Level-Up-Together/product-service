package io.pinkspider.leveluptogethermvp.towerservice.application.visitor;

import static io.pinkspider.leveluptogethermvp.towerservice.TowerTestFixtures.occupant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerEntryException;
import io.pinkspider.leveluptogethermvp.towerservice.application.exception.TowerVisitorException;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerAdminPage;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerVisitorReportAdminResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerVisitorReportRequest;
import io.pinkspider.leveluptogethermvp.towerservice.domain.dto.TowerVisitorReportResponse;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerChatLog;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerOccupant;
import io.pinkspider.leveluptogethermvp.towerservice.domain.entity.TowerVisitorReport;
import io.pinkspider.leveluptogethermvp.towerservice.domain.enums.TowerOccupantKind;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerChatLogRepository;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerOccupantRepository;
import io.pinkspider.leveluptogethermvp.towerservice.infrastructure.TowerVisitorReportRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

class TowerVisitorReportServiceTest {
    private final TowerOccupantRepository occupants = mock(TowerOccupantRepository.class);
    private final TowerVisitorReportRepository reports = mock(TowerVisitorReportRepository.class);
    private final TowerChatLogRepository chatLogs = mock(TowerChatLogRepository.class);
    private final TowerVisitorSessionStore sessions = mock(TowerVisitorSessionStore.class);
    private final TowerVisitorProperties properties = new TowerVisitorProperties();
    private TowerVisitorReportService service;

    private final TowerOccupant reporter =
            occupant(1L, 9L, 1, "u1", TowerOccupantKind.DIRECT, false);
    private final TowerOccupant visitor =
            occupant(2L, 9L, 1, "vk", TowerOccupantKind.VISITOR, false);

    @BeforeEach
    void setUp() {
        service =
                new TowerVisitorReportService(
                        occupants, reports, chatLogs, sessions, properties, new ObjectMapper());
        ReflectionTestUtils.setField(visitor, "visitorNo", 4821);
        when(occupants.findByUserId("u1")).thenReturn(Optional.of(reporter));
        when(occupants.findById(2L)).thenReturn(Optional.of(visitor));
        when(reports.save(any()))
                .thenAnswer(
                        inv -> {
                            TowerVisitorReport r = inv.getArgument(0);
                            ReflectionTestUtils.setField(r, "id", 77L);
                            return r;
                        });
    }

    @Test
    @DisplayName("LUT-705: 같은 방 방문객 신고 → 세션의 IP·기기·UA 와 최근 원문(최신 먼저, JSON)을 남긴다")
    void report() {
        when(reports.findFirstByReporterUserIdAndVisitorKeyAndCreatedAtAfterOrderByIdDesc(
                        eq("u1"), eq("vk"), any()))
                .thenReturn(Optional.empty());
        when(sessions.find("vk"))
                .thenReturn(
                        Optional.of(
                                new TowerVisitorSession("vk", 4821, "1.2.3.4", "dev-1", "UA", 1L)));
        TowerChatLog line =
                TowerChatLog.builder()
                        .instanceId(9L)
                        .originalContent("광고 www.x.com")
                        .result(TowerChatLog.RESULT_REJECTED)
                        .build();
        when(chatLogs.findTop20ByVisitorKeyOrderByIdDesc("vk")).thenReturn(List.of(line));

        TowerVisitorReportResponse res =
                service.report("u1", 9L, new TowerVisitorReportRequest(2L, "광고"));
        assertThat(res).isEqualTo(new TowerVisitorReportResponse(77L, false));
        ArgumentCaptor<TowerVisitorReport> saved =
                ArgumentCaptor.forClass(TowerVisitorReport.class);
        verify(reports).save(saved.capture());
        TowerVisitorReport r = saved.getValue();
        assertThat(r.getVisitorKey()).isEqualTo("vk");
        assertThat(r.getVisitorNo()).isEqualTo(4821);
        assertThat(r.getClientIp()).isEqualTo("1.2.3.4");
        assertThat(r.getDeviceId()).isEqualTo("dev-1");
        assertThat(r.getUserAgent()).isEqualTo("UA");
        assertThat(r.getReason()).isEqualTo("광고");
        assertThat(r.getRecentMessages())
                .contains("\"content\":\"광고 www.x.com\"")
                .contains("\"result\":\"REJECTED\"");
    }

    @Test
    @DisplayName("24시간 안에 같은 방문객을 다시 신고하면 기존 신고 ID(duplicate) · 세션이 없으면 기기 정보 없이 · 원문 조회 실패는 빈 배열")
    void duplicateAndMissing() {
        TowerVisitorReport existing = TowerVisitorReport.builder().id(5L).build();
        when(reports.findFirstByReporterUserIdAndVisitorKeyAndCreatedAtAfterOrderByIdDesc(
                        eq("u1"), eq("vk"), any()))
                .thenReturn(Optional.of(existing));
        assertThat(service.report("u1", 9L, new TowerVisitorReportRequest(2L, null)))
                .isEqualTo(new TowerVisitorReportResponse(5L, true));
        verify(reports, never()).save(any());

        when(reports.findFirstByReporterUserIdAndVisitorKeyAndCreatedAtAfterOrderByIdDesc(
                        eq("u1"), eq("vk"), any()))
                .thenReturn(Optional.empty());
        when(sessions.find("vk")).thenReturn(Optional.empty());
        when(chatLogs.findTop20ByVisitorKeyOrderByIdDesc(anyString()))
                .thenThrow(new IllegalStateException("db"));
        service.report("u1", 9L, new TowerVisitorReportRequest(2L, null));
        ArgumentCaptor<TowerVisitorReport> saved =
                ArgumentCaptor.forClass(TowerVisitorReport.class);
        verify(reports).save(saved.capture());
        assertThat(saved.getValue().getClientIp()).isNull();
        assertThat(saved.getValue().getRecentMessages()).isEqualTo("[]");
    }

    @Test
    @DisplayName("신고자가 그 방에 없으면 150205 · 대상이 그 방의 방문객이 아니면 150504")
    void rejects() {
        assertThatThrownBy(() -> service.report("u1", 10L, new TowerVisitorReportRequest(2L, null)))
                .hasFieldOrPropertyWithValue("code", TowerEntryException.NOT_IN_INSTANCE);
        when(occupants.findByUserId("nobody")).thenReturn(Optional.empty());
        assertThatThrownBy(
                        () -> service.report("nobody", 9L, new TowerVisitorReportRequest(2L, null)))
                .hasFieldOrPropertyWithValue("code", TowerEntryException.NOT_IN_INSTANCE);

        when(occupants.findById(1L)).thenReturn(Optional.of(reporter));
        assertThatThrownBy(() -> service.report("u1", 9L, new TowerVisitorReportRequest(1L, null)))
                .hasFieldOrPropertyWithValue("code", TowerVisitorException.REPORT_TARGET_NOT_FOUND);
        TowerOccupant elsewhere = occupant(3L, 11L, 2, "vk2", TowerOccupantKind.VISITOR, false);
        when(occupants.findById(3L)).thenReturn(Optional.of(elsewhere));
        assertThatThrownBy(() -> service.report("u1", 9L, new TowerVisitorReportRequest(3L, null)))
                .hasFieldOrPropertyWithValue("code", TowerVisitorException.REPORT_TARGET_NOT_FOUND);
        when(occupants.findById(4L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.report("u1", 9L, new TowerVisitorReportRequest(4L, null)))
                .hasFieldOrPropertyWithValue("code", TowerVisitorException.REPORT_TARGET_NOT_FOUND);
    }

    @Test
    @DisplayName("어드민 목록: 최신 먼저 · 방문객 키 대신 태그")
    void list() {
        TowerVisitorReport r =
                TowerVisitorReport.builder().id(5L).visitorKey("vk").visitorNo(4821).build();
        when(reports.findAllByOrderByIdDesc(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(r), PageRequest.of(0, 20), 1));
        TowerAdminPage<TowerVisitorReportAdminResponse> page = service.list(0, 20);
        assertThat(page.content().get(0).visitorTag()).isEqualTo(TowerVisitorTag.of("vk"));
        assertThat(page.content().get(0).visitorNo()).isEqualTo(4821);
    }
}
