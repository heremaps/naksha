package com.here.naksha.cli.loggers;

import org.jetbrains.annotations.NotNull;
import org.slf4j.event.Level;
import picocli.CommandLine;

public final class LoggingMixin {
    private static final CommandLoggersConfigurator loggersConfigurator = new Log4jLoggersConfigurator();

    @CommandLine.Option(
            names = {"--logLevel"},
            description = {
                    "Valid values: ${COMPLETION-CANDIDATES}"
            }
    )
    private static Level logLevel = Level.INFO;

    public static int executionStrategy(@NotNull CommandLine.ParseResult parseResult) {
        configureLoggers();
        return new CommandLine.RunLast().execute(parseResult);
    }

    private static void configureLoggers() {
        loggersConfigurator.configureLoggers(logLevel);
    }

    private LoggingMixin() {
    }
}