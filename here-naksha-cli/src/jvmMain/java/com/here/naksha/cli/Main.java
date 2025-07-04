package com.here.naksha.cli;

import com.here.naksha.cli.commands.Echo;
import picocli.CommandLine;

public class Main {
    public static void main(String[] args) {
        new CommandLine(new Echo()).execute(args);
        System.exit(0);
    }
}