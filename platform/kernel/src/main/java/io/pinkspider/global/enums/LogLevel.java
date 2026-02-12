package io.pinkspider.global.enums;

import lombok.Getter;

public enum LogLevel {

    INFO("INFO", "info log"),
    WARNING("WARNING", "warning log"),
    ERROR("ERROR", "error log");


    @Getter
    private final String level;
    @Getter
    private final String description;

    LogLevel(String level, String description) {
        this.level = level;
        this.description = description;

    }

}
