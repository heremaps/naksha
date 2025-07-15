package com.here.naksha.cli;

import picocli.CommandLine;

final class PrintExceptionMessageHandler implements CommandLine.IExecutionExceptionHandler {
    public int handleExecutionException(
            Exception ex,
            CommandLine cmd,
            CommandLine.ParseResult parseResult
    ) {

        cmd.getErr().println(cmd.getColorScheme().errorText(ex.getMessage()));
        if (ex.getCause() != null) {
            cmd.getErr().println(cmd.getColorScheme().errorText(ex.getCause().getMessage()));
        }

        return cmd.getExitCodeExceptionMapper() != null
                ? cmd.getExitCodeExceptionMapper().getExitCode(ex)
                : cmd.getCommandSpec().exitCodeOnExecutionException();
    }
}
