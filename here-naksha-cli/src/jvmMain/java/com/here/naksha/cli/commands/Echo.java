package com.here.naksha.cli.commands;

import picocli.CommandLine;

@CommandLine.Command(name="echo", description = "print provided string")
public class Echo implements Runnable {

    @CommandLine.Parameters(index = "0", description = "string to print")
    private String toEcho;

    @Override
    public void run() {
        System.out.println(toEcho);
    }
}