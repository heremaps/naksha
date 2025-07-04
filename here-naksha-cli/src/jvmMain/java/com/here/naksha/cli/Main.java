package com.here.naksha.cli;

import picocli.CommandLine;

@CommandLine.Command(name="echo", description = "print provided string")
class Echo implements Runnable {

    @CommandLine.Parameters(index = "0", description = "string to print")
    private String toEcho;

    @Override
    public void run() {
        System.out.println(toEcho);
    }
}

public class Main {
    public static void main(String[] args) {
        new CommandLine(new Echo()).execute(args);
        System.exit(0);
    }
}