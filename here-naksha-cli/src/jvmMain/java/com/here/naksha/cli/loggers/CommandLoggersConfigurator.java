package com.here.naksha.cli.loggers;

import org.jetbrains.annotations.NotNull;
import org.slf4j.event.Level;

interface CommandLoggersConfigurator {
    void configureLoggers(@NotNull Level logLevel);
}