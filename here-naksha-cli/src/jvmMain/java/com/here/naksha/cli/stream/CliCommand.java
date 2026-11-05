package com.here.naksha.cli.stream;

import org.jetbrains.annotations.NotNull;
import picocli.CommandLine;

import java.io.PrintWriter;

/** Shared by the provider, config, stream and sync commands. */
abstract class CliCommand implements Runnable {
    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    final @NotNull CliContext ctx;

    CliCommand(@NotNull CliContext ctx) {
        this.ctx = ctx;
    }

    /** Called without a subcommand. */
    @Override
    public void run() {
        throw param("Missing subcommand, see: " + spec.qualifiedName() + " --help");
    }

    @NotNull PrintWriter out() {
        return spec.commandLine().getOut();
    }

    @NotNull CommandLine.ParameterException param(@NotNull String message) {
        return new CommandLine.ParameterException(spec.commandLine(), message);
    }
}
