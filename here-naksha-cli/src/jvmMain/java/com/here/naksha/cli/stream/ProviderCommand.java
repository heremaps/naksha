package com.here.naksha.cli.stream;

import com.here.naksha.cli.VersionInfo;
import naksha.model.IStorageProvider;
import org.jetbrains.annotations.NotNull;
import picocli.CommandLine;

@CommandLine.Command(
        name = "provider",
        mixinStandardHelpOptions = true,
        description = "Lists the storage providers.",
        versionProvider = VersionInfo.class
)
public final class ProviderCommand extends CliCommand {
    public ProviderCommand(@NotNull CliContext ctx) {
        super(ctx);
    }

    @CommandLine.Command(name = "list", description = "Lists all providers with name and description.")
    int list() {
        for (IStorageProvider p : ctx.providers.all()) out().printf("%-8s %s%n", p.getName(), p.getDescription());
        return CommandLine.ExitCode.OK;
    }
}
