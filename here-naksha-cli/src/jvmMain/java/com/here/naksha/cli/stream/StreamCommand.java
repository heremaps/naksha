package com.here.naksha.cli.stream;

import com.here.naksha.cli.VersionInfo;
import com.here.naksha.cli.stream.StreamDefinition.Endpoint;
import com.here.naksha.cli.stream.StreamDefinition.Options;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@CommandLine.Command(
        name = "stream",
        mixinStandardHelpOptions = true,
        description = "Defines what to copy from where to where.",
        versionProvider = VersionInfo.class
)
public final class StreamCommand extends CliCommand {
    public StreamCommand(@NotNull CliContext ctx) {
        super(ctx);
    }

    @CommandLine.Command(name = "create", description = "Asks for a name, the source, the targets and the options, and saves the stream.")
    int create(@CommandLine.Option(names = "--name", description = "The name of the stream.") @Nullable String name) {
        PrintWriter out = out();
        List<String> storages = ctx.configs.names();
        if (storages.isEmpty()) throw param("No storages configured, create them with: naksha-cli config create");
        Questions q = ctx.questions(out);
        if (name == null) name = q.ask("Name of the stream", null);
        try {
            JsonFileStore.requireValidName(name);
        } catch (IllegalArgumentException e) {
            throw param(e.getMessage());
        }
        if (ctx.streams.get(name).isPresent()) throw param("Stream '" + name + "' exists already, delete it first");
        String storage = q.ask("Source storage", storages, null, false);
        String catalog = q.ask("Source catalog", "default");
        String collection = q.ask("Source collection", "default");
        Endpoint source = new Endpoint(storage, catalog, collection);
        List<Endpoint> targets = new ArrayList<>();
        do {
            targets.add(new Endpoint(
                    q.ask("Target storage", storages, null, false),
                    q.ask("Target catalog", catalog),
                    q.ask("Target collection", collection)));
        } while (q.askYesNo("Add another target?", false));
        Options options = new Options(
                q.askYesNo("Copy the history?", true),
                q.askYesNo("Copy deleted features?", true),
                !q.askYesNo("Keep the transactions of the source?", true),
                q.askInt("Chunk size", 1000));
        ctx.streams.save(name, new StreamDefinition(source, targets, options, StreamDefinition.State.NEW));
        out.printf("Saved %s.%n", name);
        return CommandLine.ExitCode.OK;
    }

    @CommandLine.Command(name = "list", description = "Lists all streams and their state.")
    int list() {
        List<String> names = ctx.streams.names();
        if (names.isEmpty()) {
            out().println("No streams defined, create one with: naksha-cli stream create");
            return CommandLine.ExitCode.OK;
        }
        String format = "%-16s %-32s %-32s %-12s %s%n";
        out().printf(format, "NAME", "SOURCE", "TARGETS", "STATUS", "ACKNOWLEDGED");
        for (String n : names) {
            StreamDefinition d = ctx.streams.get(n).orElseThrow();
            String targets = d.targets().stream().map(Endpoint::toString).collect(Collectors.joining(", "));
            out().printf(format, n, d.source(), targets, d.state().status(), d.state().acknowledgedTuples());
        }
        return CommandLine.ExitCode.OK;
    }

    @CommandLine.Command(name = "delete", description = "Deletes a stream.")
    int delete(@CommandLine.Parameters(paramLabel = "NAME", description = "The name of the stream.") @NotNull String name) {
        if (!ctx.streams.delete(name)) throw param("Unknown stream '" + name + "'");
        out().printf("Deleted %s.%n", name);
        return CommandLine.ExitCode.OK;
    }
}
