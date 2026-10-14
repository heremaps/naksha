package com.here.naksha.cli.stream;

import com.here.naksha.cli.VersionInfo;
import com.here.naksha.cli.stream.StreamDefinition.Endpoint;
import com.here.naksha.cli.stream.StreamDefinition.State;
import naksha.model.IStorageProvider;
import naksha.model.IStreamSession;
import naksha.model.NakshaContext;
import naksha.model.SessionOptions;
import naksha.model.streaming.Stream;
import naksha.model.streaming.StreamRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

@CommandLine.Command(
        name = "sync",
        mixinStandardHelpOptions = true,
        description = "Runs a stream, with --continue from its latest recovery request.",
        versionProvider = VersionInfo.class
)
public final class SyncCommand implements Callable<Integer> {
    static final int EXIT_INTERRUPTED = 130;
    static final long SAVE_INTERVAL_MILLIS = 1000L;

    @CommandLine.Spec
    private CommandLine.Model.CommandSpec spec;

    @CommandLine.Parameters(paramLabel = "NAME", description = "The name of the stream.")
    private String name;

    @CommandLine.Option(names = "--continue", description = "Continue from the latest recovery request, for example after an abort or to read new data.")
    private boolean continueStream;

    private final CliContext ctx;

    public SyncCommand(@NotNull CliContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public Integer call() {
        PrintWriter out = spec.commandLine().getOut();
        StreamDefinition definition = ctx.streams.get(name)
                .orElseThrow(() -> param("Unknown stream '" + name + "', see: naksha-cli stream list"));
        StreamRequest request;
        if (continueStream) {
            String saved = definition.state().recoveryRequest();
            if (saved == null) throw param("Stream '" + name + "' has no saved state, run it without --continue");
            request = StreamRequestJson.fromJson(saved);
        } else {
            request = definition.newRequest();
        }

        NakshaContext.currentContext().withAppId("nakshacli");
        SessionOptions options = SessionOptions.from(NakshaContext.currentContext());
        List<IStreamSession> sessions = new ArrayList<>();
        try {
            IStreamSession source = open(definition.source(), options, sessions);
            if (!source.getMayRead()) throw param("Storage '" + definition.source().storage() + "' can't be used as source");
            List<IStreamSession> targets = new ArrayList<>();
            for (Endpoint t : definition.targets()) {
                IStreamSession target = open(t, options, sessions);
                if (!target.getMayWrite()) throw param("Storage '" + t.storage() + "' can't be used as target");
                targets.add(target);
            }

            Stream stream = source.read(request);
            long before = continueStream ? definition.state().acknowledgedTuples() : 0L;
            StateWriter state = new StateWriter(definition, before, stream);
            SyncService service = new SyncService(SAVE_INTERVAL_MILLIS, r -> state.save("Running", r), s -> out.printf(
                    "\racknowledged %d, in flight %d, estimated %d", s.getAcknowledgedTuples(), s.getUnacknowledgedTuples(), s.getEstimatedTuples()));

            AtomicBoolean interrupted = new AtomicBoolean();
            Thread hook = new Thread(() -> {
                interrupted.set(true);
                saveOnInterrupt(stream, state, out);
                // The JVM exits without running the finally block of the main thread.
                closeQuietly(sessions, out);
            });
            Runtime.getRuntime().addShutdownHook(hook);
            try {
                SyncService.Result result = service.sync(stream, targets);
                if (interrupted.get()) return EXIT_INTERRUPTED;
                out.println();
                if (result instanceof SyncService.Done done) {
                    state.save("Done", done.recoveryRequest());
                    out.printf("Success! Copied %d tuples.%n", done.tuples());
                    return CommandLine.ExitCode.OK;
                }
                SyncService.Aborted aborted = (SyncService.Aborted) result;
                state.save("Failed", aborted.recoveryRequest());
                out.printf("Aborted: %s: %s%n", aborted.error().getCode(), aborted.error().getMsg());
                if (aborted.recoveryRequest() != null) {
                    out.printf("Saved the state of %s, continue with: naksha-cli sync %s --continue%n", name, name);
                } else {
                    out.println("The stream is not recoverable.");
                }
                return CommandLine.ExitCode.SOFTWARE;
            } finally {
                try {
                    Runtime.getRuntime().removeShutdownHook(hook);
                } catch (IllegalStateException ignored) {
                    // the JVM is already shutting down
                }
            }
        } finally {
            for (IStreamSession s : sessions) s.close();
        }
    }

    private IStreamSession open(Endpoint endpoint, SessionOptions options, List<IStreamSession> sessions) {
        StorageConfig config = ctx.configs.get(endpoint.storage())
                .orElseThrow(() -> param("Unknown storage '" + endpoint.storage() + "' in stream '" + name + "', see: naksha-cli config list"));
        IStorageProvider provider;
        try {
            provider = ctx.providers.get(config.provider());
        } catch (IllegalArgumentException e) {
            throw param(e.getMessage());
        }
        IStreamSession session = provider.getStorage(config.config()).newStreamSession(options);
        sessions.add(session);
        return session;
    }

    private void saveOnInterrupt(Stream stream, StateWriter state, PrintWriter out) {
        if (stream.isClosed() || !stream.isRecoverable()) return;
        try {
            state.save("Interrupted", stream.closeForRecovery());
            out.println();
            out.printf("Interrupted, saved the state of %s.%nContinue with: naksha-cli sync %s --continue%n", name, name);
        } catch (Exception e) {
            out.printf("%nInterrupted, could not save the state: %s%n", e.getMessage());
        }
        out.flush();
    }

    private static void closeQuietly(List<IStreamSession> sessions, PrintWriter out) {
        for (IStreamSession s : sessions) {
            try {
                s.close();
            } catch (Exception e) {
                out.printf("Could not close a session: %s%n", e.getMessage());
            }
        }
        out.flush();
    }

    private CommandLine.ParameterException param(String message) {
        return new CommandLine.ParameterException(spec.commandLine(), message);
    }

    /** Saves the state into the stream, called by the reading thread and the shutdown hook. */
    private final class StateWriter {
        private final long before;
        private final Stream stream;
        private StreamDefinition definition;

        StateWriter(StreamDefinition definition, long before, Stream stream) {
            this.definition = definition;
            this.before = before;
            this.stream = stream;
        }

        synchronized void save(@NotNull String status, @Nullable StreamRequest recovery) {
            String json = recovery != null ? StreamRequestJson.toJson(recovery) : definition.state().recoveryRequest();
            definition = definition.withState(new State(status, Instant.now().toString(), before + stream.getAcknowledgedTuples(), json));
            ctx.streams.save(name, definition);
        }
    }
}
