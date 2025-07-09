package com.here.naksha.cli;

import com.here.naksha.cli.copy.CopyCommand;
import picocli.CommandLine;

@CommandLine.Command(
        name = "naksha-cli",
        mixinStandardHelpOptions = true,
        subcommands = {
                CopyCommand.class
        }
)
public class NakshaCliCommand {
}
