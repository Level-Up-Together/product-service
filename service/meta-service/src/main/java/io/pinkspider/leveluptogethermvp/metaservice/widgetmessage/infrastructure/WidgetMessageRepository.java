package io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.infrastructure;

import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.entity.WidgetMessage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WidgetMessageRepository extends JpaRepository<WidgetMessage, Long> {

    List<WidgetMessage> findAllByOrderByStartHourAscSortOrderAscIdAsc();

    List<WidgetMessage> findByActiveTrueOrderByStartHourAscSortOrderAscIdAsc();

    /** LUT-797: 그 배경 이미지를 쓰는 문장 수 — 0 이 되면 파일을 지운다 */
    long countByBackgroundImageUrl(String backgroundImageUrl);
}
