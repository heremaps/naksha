package com.here.naksha.cli;

import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

public class TestCommandLine {
    private final CommandLine commandLine;
    private final StringWriter out = new StringWriter();
    private final StringWriter err = new StringWriter();

    public TestCommandLine(Object cmd) {
        commandLine = new CommandLine(cmd);
        commandLine.setParameterExceptionHandler(new ShortErrorMessageHandler());
        commandLine.setOut(new PrintWriter(out));
        commandLine.setErr(new PrintWriter(err));
    }

    public CommandResult execute(String... args) {
        int exitCode = commandLine.execute(args);
        return new CommandResult(
                exitCode,
                readLinesFromString(out.toString()),
                readLinesFromString(err.toString())
        );
    }

    public record CommandResult(
            int exitCode,
            List<String> stdOut,
            List<String> stdErr
    ) {
    }

    private List<String> readLinesFromString(String str) {
        return str.isEmpty() ? List.of() : str.lines().toList();
    }
}
