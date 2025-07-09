package com.here.naksha.cli;

import picocli.CommandLine;

public class Main {
    public static void main(String[] args) {
        CommandLine cmd = new CommandLine(new NakshaCliCommand());
        cmd.setParameterExceptionHandler(new ShortErrorMessageHandler());
        int exitCode = cmd.execute(args);
        System.exit(exitCode);
    }
}