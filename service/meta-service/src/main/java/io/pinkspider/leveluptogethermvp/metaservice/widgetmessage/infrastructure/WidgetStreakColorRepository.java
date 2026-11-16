package io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.infrastructure;

import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.entity.WidgetStreakColor;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** LUT-816: 위젯 'N일' 숫자 색 구간 */
public interface WidgetStreakColorRepository extends JpaRepository<WidgetStreakColor, Long> {

    List<WidgetStreakColor> findAllByOrderByMinDaysAsc();
}
