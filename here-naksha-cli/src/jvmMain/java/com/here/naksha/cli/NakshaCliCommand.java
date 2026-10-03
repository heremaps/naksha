package com.here.naksha.cli;

import com.here.naksha.cli.copy.CopyCommand;
import com.here.naksha.cli.loggers.LoggingMixin;
import com.here.naksha.cli.stream.StreamCopyCommand;
import picocli.CommandLine;

@CommandLine.Command(
        name = "naksha-cli",
        mixinStandardHelpOptions = true,
        subcommands = {
                CopyCommand.class,
                StreamCopyCommand.class
        },
        versionProvider = VersionInfo.class,
        showDefaultValues = true
)
public final class NakshaCliCommand {
    @CommandLine.Mixin
    private LoggingMixin loggingMixin;
}