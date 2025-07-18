package com.here.naksha.cli;

import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;

public class TestCommandLine {
    private final CommandLine commandLine;
    private final StringWriter out = new StringWriter();
    private final StringWriter err = new StringWriter();

    public TestCommandLine(Object cmd) {
        CommandFactory commandFactory = new CommandFactory();
        commandLine = new CommandLine(cmd, commandFactory);
        commandLine.setParameterExceptionHandler(new ShortErrorMessageHandler());
        commandLine.setExecutionExceptionHandler(new PrintExceptionMessageHandler());
        commandLine.setOut(new PrintWriter(out));
        commandLine.setErr(new PrintWriter(err));
    }

    public CommandResult execute(String... args) {
        int exitCode = commandLine.execute(args);
        return new CommandResult(
                exitCode,
                out.toString(),
                err.toString()
        );
    }

    public record CommandResult(
            int exitCode,
            String stdOut,
            String stdErr
    ) {
    }
}
