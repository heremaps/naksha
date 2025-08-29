package com.here.naksha.cli.loggers;

import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.jetbrains.annotations.NotNull;
import org.slf4j.event.Level;

final class Log4jLoggersConfigurator implements CommandLoggersConfigurator {
    @Override
    public void configureLoggers(@NotNull Level logLevel) {
        org.apache.logging.log4j.Level mappedLevel = mapLogLevel(logLevel);
        LoggerContext loggerContext = LoggerContext.getContext(false);
        LoggerConfig config = loggerContext.getConfiguration().getRootLogger();
        for (Appender appender : config.getAppenders().values()) {
            if (appender instanceof ConsoleAppender) {
                config.removeAppender(appender.getName());
                config.addAppender(appender, mappedLevel, null);
            }
        }
        if (config.getLevel().isMoreSpecificThan(mappedLevel)) {
            config.setLevel(mappedLevel);
        }
        loggerContext.updateLoggers();
    }


    private org.apache.logging.log4j.Level mapLogLevel(Level level) {
        return switch (level) {
            case ERROR -> org.apache.logging.log4j.Level.ERROR;
            case WARN -> org.apache.logging.log4j.Level.WARN;
            case INFO -> org.apache.logging.log4j.Level.INFO;
            case DEBUG -> org.apache.logging.log4j.Level.DEBUG;
            case TRACE -> org.apache.logging.log4j.Level.TRACE;
        };
    }
}
