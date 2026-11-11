package io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.infrastructure;

import io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.domain.entity.WidgetMessage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WidgetMessageRepository extends JpaRepository<WidgetMessage, Long> {

    List<WidgetMessage> findAllByOrderByStartHourAscSortOrderAscIdAsc();

    List<WidgetMessage> findByActiveTrueOrderByStartHourAscSortOrderAscIdAsc();
}
