package com.here.naksha.cli.stream;

import naksha.base.NakshaError;
import naksha.base.NakshaException;
import naksha.model.IStreamSession;
import naksha.model.streaming.Stream;
import naksha.model.streaming.StreamChunk;
import naksha.model.streaming.StreamException;
import naksha.model.streaming.StreamRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;

/**
 * DRAFT: reads one stream and writes every chunk into all targets, following the read loop documented in {@link Stream}.
 */
public final class StreamCopyService {
    private static final Logger log = LoggerFactory.getLogger(StreamCopyService.class);

    public sealed interface Result permits Done, Aborted {}

    /** All chunks were written to all targets. */
    public record Done(long tuples) implements Result {}

    /** Copy stopped; resume with {@code recoveryRequest} if not null. */
    public record Aborted(@NotNull NakshaError error, @Nullable StreamRequest recoveryRequest) implements Result {}

    private final int maxInFlight;
    private final Consumer<Stream> progress;

    /**
     * @param maxInFlight maximum number of concurrent target writes.
     * @param progress called after each chunk is dispatched.
     */
    public StreamCopyService(int maxInFlight, @NotNull Consumer<Stream> progress) {
        if (maxInFlight <= 0) throw new IllegalArgumentException("maxInFlight must be > 0");
        this.maxInFlight = maxInFlight;
        this.progress = progress;
    }

    public @NotNull Result copy(@NotNull Stream stream, @NotNull List<IStreamSession> targets) {
        if (targets.isEmpty()) throw new IllegalArgumentException("At least one target is required");
        Semaphore permits = new Semaphore(maxInFlight);
        try {
            try (ExecutorService writers = Executors.newVirtualThreadPerTaskExecutor()) {
                while (stream.hasNext()) {
                    StreamChunk chunk = stream.next();
                    chunk.setTargets(targets.size());
                    for (IStreamSession target : targets) {
                        permits.acquire();
                        writers.submit(() -> write(target, chunk, permits));
                    }
                    progress.accept(stream);
                }
            }
            // All writers finished; close verifies acknowledgements and that nothing is left to read.
            // Closed from outside, for example by the Ctrl-C hook, so not everything was copied.
            if (stream.isClosed()) {
                return new Aborted(new NakshaError(NakshaError.CLOSED, "Stream was closed before all chunks were copied", null), null);
            }
            long tuples = stream.getAcknowledgedTuples();
            stream.close();
            return new Done(tuples);
        } catch (StreamException e) {
            return new Aborted(e.getError(), e.getRecoveryRequest());
        } catch (NakshaException e) {
            return new Aborted(e.getError(), recoverQuietly(stream));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Aborted(new NakshaError(NakshaError.CLOSED, "Interrupted", e), recoverQuietly(stream));
        }
    }

    private static void write(IStreamSession target, StreamChunk chunk, Semaphore permits) {
        try {
            target.write(chunk);
        } catch (NakshaException e) {
            log.error("Write failed", e);
            chunk.fail(e.getError());
        } catch (Throwable t) {
            log.error("Write failed", t);
            chunk.fail(new NakshaError(NakshaError.EXCEPTION, String.valueOf(t.getMessage()), t));
        } finally {
            permits.release();
        }
    }

    private static @Nullable StreamRequest recoverQuietly(Stream stream) {
        if (stream.isClosed() || !stream.isRecoverable()) return null;
        try {
            return stream.closeForRecovery();
        } catch (Exception e) {
            log.warn("Could not create a recovery request", e);
            return null;
        }
    }
}
