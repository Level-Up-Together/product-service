package io.pinkspider.leveluptogethermvp.metaservice.widgetmessage.application;

/** LUT-775: 없는 위젯 문장 — {@code WidgetMessageExceptionHandler} 가 404 {@code 080201} 로 바꾼다 */
public class WidgetMessageNotFoundException extends RuntimeException {

    public WidgetMessageNotFoundException(Long id) {
        super("widget message not found: id=" + id);
    }
}
