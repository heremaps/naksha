package com.here.naksha.cli;

import picocli.CommandLine;

final class PrintExceptionMessageHandler implements CommandLine.IExecutionExceptionHandler {
    public int handleExecutionException(
            Exception ex,
            CommandLine cmd,
            CommandLine.ParseResult parseResult
    ) {
        Throwable cause = ex;
        while (cause != null) {
            cmd.getErr().println(cmd.getColorScheme().errorText(cause.getMessage()));
            cause = cause.getCause();
        }

        return cmd.getExitCodeExceptionMapper() != null
                ? cmd.getExitCodeExceptionMapper().getExitCode(ex)
                : cmd.getCommandSpec().exitCodeOnExecutionException();
    }
}
