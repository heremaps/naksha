package com.here.naksha.cli;

import com.here.naksha.cli.loggers.LoggingMixin;
import picocli.CommandLine;

final class Main {
    public static void main(String[] args) {
        CommandLine cmd = new CommandLine(new NakshaCliCommand(), new CommandFactory())
                .setExecutionStrategy(LoggingMixin::executionStrategy)
                .setParameterExceptionHandler(new ShortErrorMessageHandler())
                .setExecutionExceptionHandler(new PrintExceptionMessageHandler());
        int exitCode = cmd.execute(args);
        System.exit(exitCode);
    }
}