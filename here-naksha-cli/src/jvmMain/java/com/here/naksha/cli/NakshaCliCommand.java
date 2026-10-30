package com.here.naksha.cli;

import com.here.naksha.cli.copy.CopyCommand;
import com.here.naksha.cli.loggers.LoggingMixin;
import com.here.naksha.cli.stream.ConfigCommand;
import com.here.naksha.cli.stream.ProviderCommand;
import com.here.naksha.cli.stream.StreamCommand;
import com.here.naksha.cli.stream.SyncCommand;
import picocli.CommandLine;

@CommandLine.Command(
        name = "naksha-cli",
        mixinStandardHelpOptions = true,
        subcommands = {
                CopyCommand.class,
                ProviderCommand.class,
                ConfigCommand.class,
                StreamCommand.class,
                SyncCommand.class
        },
        versionProvider = VersionInfo.class,
        showDefaultValues = true
)
public final class NakshaCliCommand {
    @CommandLine.Mixin
    private LoggingMixin loggingMixin;
}