package com.here.naksha.cli;

import com.here.naksha.cli.copy.ShortErrorMessageHandler;
import picocli.CommandLine;

public class Main {
    public static void main(String[] args) {
        CommandLine cmd = new CommandLine(new NakshaCLICommand());
        cmd.setParameterExceptionHandler(new ShortErrorMessageHandler());
        int exitCode = cmd.execute(args);
        System.exit(exitCode);
    }
}