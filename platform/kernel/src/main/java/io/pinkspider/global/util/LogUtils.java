package io.pinkspider.global.util;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LogUtils {

    private static StringBuilder builder = new StringBuilder();
    private static final String PREFIX_FILE_NAME = "file name : ";
    private static final String PREFIX_CLASS_NAME = "class name : ";
    private static final String PREFIX_LINE_NUMBER = "line number : ";
    private static final String PREFIX_METHOD_NAME = "method name : ";

    private static final String LINE_BREAK = "\n";


    public static String makeSlackLogMessage(Exception exception) {
        StackTraceElement[] stackTraceElements = exception.getStackTrace();
        if (stackTraceElements == null) {
            log.error("Exception is null or empty");
        }

        StackTraceElement firstElement = exception.getStackTrace()[0];
        assert stackTraceElements != null;
        StackTraceElement lastElement = stackTraceElements[stackTraceElements.length - 1];

        String firstLogMessage = makeSlackLogMessage(firstElement);
        String lastLogMessage = makeSlackLogMessage(lastElement);

        builder.append(exception.getMessage());
        builder.append(firstLogMessage);
        builder.append(lastLogMessage);

        String result = builder.toString();
        builder.setLength(0);
        return result;
    }

    private static String makeSlackLogMessage(StackTraceElement stackTraceElement) {
        builder.append(PREFIX_FILE_NAME).append(stackTraceElement.getFileName()).append(LINE_BREAK);
        builder.append(PREFIX_CLASS_NAME).append(stackTraceElement.getClassName()).append(LINE_BREAK);
        builder.append(PREFIX_LINE_NUMBER).append(stackTraceElement.getLineNumber()).append(LINE_BREAK);
        builder.append(PREFIX_METHOD_NAME).append(stackTraceElement.getMethodName()).append(LINE_BREAK);

        String result = builder.toString();
        builder.setLength(0);
        return result;
    }
}
