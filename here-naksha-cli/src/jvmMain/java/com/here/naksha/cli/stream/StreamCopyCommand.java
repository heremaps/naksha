package com.here.naksha.cli.stream;

import com.here.naksha.cli.VersionInfo;
import com.here.naksha.cli.loggers.LoggingMixin;
import naksha.base.Id;
import naksha.base.Version;
import naksha.model.IStreamSession;
import naksha.model.NakshaContext;
import naksha.model.SessionOptions;
import naksha.model.streaming.Stream;
import naksha.model.streaming.StreamRequest;
import naksha.model.streaming.StreamRequestBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import picocli.CommandLine;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Copies one collection from a source into one or more targets, using the streaming API.
 * Sources and targets are {@link StreamProvider}s, selected by name.
 */
@CommandLine.Command(
        name = "stream-copy",
        mixinStandardHelpOptions = true,
        description = "Stream a collection from a source into one or more targets.",
        sortOptions = false,
        showDefaultValues = true,
        versionProvider = VersionInfo.class
)
public final class StreamCopyCommand implements Callable<Integer> {
    static final String DEFAULT_ID = "default";

    @CommandLine.Spec
    private CommandLine.Model.CommandSpec spec;

    @CommandLine.Mixin
    private LoggingMixin loggingMixin;

    @CommandLine.Option(names = "--source", description = "The name of the source provider.")
    private @Nullable String source;

    @CommandLine.Option(names = "--sourceConfig", description = "The configuration file of the source.")
    private @Nullable Path sourceConfig;

    @CommandLine.Option(names = "--target", description = "The name of a target provider, can be repeated.")
    private List<String> targets = new ArrayList<>();

    @CommandLine.Option(names = "--targetConfig", description = "The configuration file of the target, one per --target, or none at all.")
    private List<Path> targetConfigs = new ArrayList<>();

    @CommandLine.Option(names = "--databaseId", description = "The value for StreamRequest.databaseId; defaults to the source name.")
    private @Nullable String databaseId;

    @CommandLine.Option(names = "--catalog", description = "The catalog to read, required unless resuming.", defaultValue = DEFAULT_ID)
    private String catalog = DEFAULT_ID;

    @CommandLine.Option(names = "--collection", description = "The collection to read, required unless resuming.", defaultValue = DEFAULT_ID)
    private String collection = DEFAULT_ID;

    @CommandLine.Option(names = "--minVersion", description = "The lowest version to read (inclusive).")
    private long minVersion = 0L;

    @CommandLine.Option(names = "--maxVersion", description = "The highest version to read (inclusive); defaults to HEAD.")
    private @Nullable Long maxVersion;

    @CommandLine.Option(names = "--headOnly", description = "Only read the latest state of every feature, implies --ignoreTransactions.")
    private boolean headOnly;

    @CommandLine.Option(names = "--excludeDeleted", description = "Do not read deleted features.")
    private boolean excludeDeleted;

    @CommandLine.Option(names = "--ignoreTransactions", description = "Only keep the order per feature.")
    private boolean ignoreTransactions;

    @CommandLine.Option(names = "--sequential", description = "Process only one chunk at a time.")
    private boolean sequential;

    @CommandLine.Option(names = "--chunkSize", description = "The amount of tuples per chunk, when transactions are ignored or the source has no transactions.")
    private int chunkSize = 1000;

    @CommandLine.Option(names = "--maxInFlight", description = "The maximal amount of concurrent writes.")
    private int maxInFlight = 64;

    @CommandLine.Option(names = "--recoveryFile", description = "Where to write the recovery request, when the copy aborts.")
    private Path recoveryFile = Path.of("stream-recovery.json");

    @CommandLine.Option(names = "--resume", description = "Resume from the given recovery file.")
    private @Nullable Path resume;

    @CommandLine.Option(names = "--list", description = "Print all available providers and exit.")
    private boolean list;

    private final StreamProviders providers;

    public StreamCopyCommand(@NotNull StreamProviders providers) {
        this.providers = providers;
    }

    @Override
    public Integer call() throws IOException {
        PrintWriter out = spec.commandLine().getOut();
        if (list) {
            for (StreamProvider p : providers.all()) out.printf("%-8s %s%n", p.name(), p.description());
            return CommandLine.ExitCode.OK;
        }
        validate();

        NakshaContext.currentContext().withAppId("nakshacli");
        SessionOptions options = SessionOptions.from(NakshaContext.currentContext());

        StreamProvider sourceProvider = providers.get(source);
        List<StreamProvider> targetProviders = new ArrayList<>();
        for (String t : targets) targetProviders.add(providers.get(t));

        IStreamSession sourceSession = sourceProvider.open(sourceConfig, options);
        List<IStreamSession> targetSessions = new ArrayList<>();
        try {
            if (!sourceSession.getMayRead()) throw param("Provider '" + source + "' can't be used as source");
            for (int i = 0; i < targetProviders.size(); i++) {
                Path config = targetConfigs.isEmpty() ? null : targetConfigs.get(i);
                IStreamSession session = targetProviders.get(i).open(config, options);
                targetSessions.add(session);
                if (!session.getMayWrite()) throw param("Provider '" + targets.get(i) + "' can't be used as target");
            }

            StreamRequest request = resume != null ? StreamRequestJson.fromJson(Files.readString(resume)) : buildRequest();
            Stream stream = sourceSession.read(request);
            StreamCopyService service = new StreamCopyService(maxInFlight, s -> out.printf(
                    "\racknowledged %d, in flight %d, estimated %d", s.getAcknowledgedTuples(), s.getUnacknowledgedTuples(), s.getEstimatedTuples()));

            Thread hook = new Thread(() -> saveRecoveryOnShutdown(stream, out));
            Runtime.getRuntime().addShutdownHook(hook);
            try {
                StreamCopyService.Result result = service.copy(stream, targetSessions);
                out.println();
                if (result instanceof StreamCopyService.Done done) {
                    out.printf("Success! Copied %d tuples.%n", done.tuples());
                    return CommandLine.ExitCode.OK;
                }
                StreamCopyService.Aborted aborted = (StreamCopyService.Aborted) result;
                out.printf("Aborted: %s: %s%n", aborted.error().getCode(), aborted.error().getMsg());
                if (aborted.recoveryRequest() != null) {
                    writeRecovery(aborted.recoveryRequest(), out);
                } else {
                    out.println("The stream is not recoverable.");
                }
                return CommandLine.ExitCode.SOFTWARE;
            } finally {
                removeHook(hook);
            }
        } finally {
            for (IStreamSession t : targetSessions) t.close();
            sourceSession.close();
        }
    }

    private void validate() {
        if (source == null) throw param("--source is required");
        if (targets.isEmpty()) throw param("At least one --target is required");
        if (!targetConfigs.isEmpty() && targetConfigs.size() != targets.size()) {
            throw param("Give one --targetConfig per --target, or none at all");
        }
        if (maxInFlight <= 0) throw param("--maxInFlight must be > 0");
        if (chunkSize <= 0) throw param("--chunkSize must be > 0");
        try {
            providers.get(source);
            for (String t : targets) providers.get(t);
        } catch (IllegalArgumentException e) {
            throw param(e.getMessage());
        }
    }

    private StreamRequest buildRequest() {
        String db = databaseId != null ? databaseId : source;
        return new StreamRequestBuilder(new Id(db), new Id(catalog), new Id(collection))
                .withMinVersion(minVersion)
                .withVersion(maxVersion != null ? maxVersion : Version.HEAD.number)
                .withQueryHistory(!headOnly)
                .withQueryDeleted(!excludeDeleted)
                // A source with transactions must deliver them even for HEAD only, so HEAD only implies ignoring them.
                .withIgnoreTransactions(ignoreTransactions || headOnly)
                .withSequential(sequential)
                .withChunkSize(chunkSize)
                .build();
    }

    private CommandLine.ParameterException param(String message) {
        return new CommandLine.ParameterException(spec.commandLine(), message);
    }

    private void writeRecovery(StreamRequest recovery, PrintWriter out) {
        String json = StreamRequestJson.toJson(recovery);
        out.println("Recovery request:");
        out.println(json);
        try {
            Files.writeString(recoveryFile, json);
            out.printf("Saved to %s, resume with --resume %s%n", recoveryFile, recoveryFile);
        } catch (IOException e) {
            out.printf("Could not write %s: %s%n", recoveryFile, e.getMessage());
        }
        out.flush();
    }

    private void saveRecoveryOnShutdown(Stream stream, PrintWriter out) {
        if (stream.isClosed() || !stream.isRecoverable()) return;
        try {
            out.println();
            out.println("Interrupted.");
            writeRecovery(stream.closeForRecovery(), out);
        } catch (Exception e) {
            out.printf("Could not create a recovery request: %s%n", e.getMessage());
            out.flush();
        }
    }

    private static void removeHook(Thread hook) {
        try {
            Runtime.getRuntime().removeShutdownHook(hook);
        } catch (IllegalStateException ignored) {
            // the JVM is already shutting down
        }
    }
}
