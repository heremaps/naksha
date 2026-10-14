package com.here.naksha.cli.stream;

import com.here.naksha.cli.VersionInfo;
import naksha.model.IStorageProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.util.List;

@CommandLine.Command(
        name = "config",
        mixinStandardHelpOptions = true,
        description = "Configures storages.",
        versionProvider = VersionInfo.class
)
public final class ConfigCommand extends CliCommand {
    public ConfigCommand(@NotNull CliContext ctx) {
        super(ctx);
    }

    @CommandLine.Command(name = "create", description = "Asks for a name and a provider, runs the questions of the provider, and saves the configuration.")
    int create(
            @CommandLine.Option(names = "--name", description = "The name of the storage.") @Nullable String name,
            @CommandLine.Option(names = "--provider", description = "The name of the provider.") @Nullable String provider
    ) {
        PrintWriter out = out();
        Questions q = ctx.questions(out);
        if (name == null) name = q.ask("Name of the storage", null);
        try {
            JsonFileStore.requireValidName(name);
        } catch (IllegalArgumentException e) {
            throw param(e.getMessage());
        }
        if (ctx.configs.get(name).isPresent()) throw param("Storage '" + name + "' exists already, delete it first");
        List<String> names = ctx.providers.all().stream().map(IStorageProvider::getName).toList();
        if (provider == null) provider = q.ask("Provider", names, null, false);
        if (!names.contains(provider)) throw param("Unknown storage provider '" + provider + "', available: " + String.join(", ", names));
        String config = q.run(ctx.providers.get(provider).newConfigBuilder());
        ctx.configs.save(name, new StorageConfig(name, provider, config));
        out.printf("Saved %s.%n", name);
        return CommandLine.ExitCode.OK;
    }

    @CommandLine.Command(name = "list", description = "Lists the configured storages.")
    int list() {
        List<String> all = ctx.configs.names();
        if (all.isEmpty()) {
            out().println("No storages configured, create one with: naksha-cli config create");
            return CommandLine.ExitCode.OK;
        }
        out().printf("%-20s %s%n", "NAME", "PROVIDER");
        for (String n : all) {
            ctx.configs.get(n).ifPresent(c -> out().printf("%-20s %s%n", n, c.provider()));
        }
        return CommandLine.ExitCode.OK;
    }

    @CommandLine.Command(name = "delete", description = "Deletes a configured storage.")
    int delete(@CommandLine.Parameters(paramLabel = "NAME", description = "The name of the storage.") @NotNull String name) {
        if (!ctx.configs.delete(name)) throw param("Unknown storage '" + name + "'");
        out().printf("Deleted %s.%n", name);
        return CommandLine.ExitCode.OK;
    }
}
