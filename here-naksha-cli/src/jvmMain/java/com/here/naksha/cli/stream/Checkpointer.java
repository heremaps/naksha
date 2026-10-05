package com.here.naksha.cli.stream;

import naksha.model.streaming.QueueStream;
import naksha.model.streaming.Stream;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Periodically writes the current recovery request, so that a crash, which skips the shutdown hook, loses at most one interval. */
public final class Checkpointer implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(Checkpointer.class);

    private final ScheduledExecutorService timer;
    private volatile boolean written;

    private Checkpointer(ScheduledExecutorService timer) {
        this.timer = timer;
    }

    /** Starts writing checkpoints, or returns {@code null} if the interval is not positive or the stream does not support it. */
    public static Checkpointer start(@NotNull Stream stream, @NotNull Path file, long intervalMillis) {
        if (intervalMillis <= 0) return null;
        if (!(stream instanceof QueueStream queue)) {
            log.warn("The source stream does not support checkpoints, only the shutdown hook writes a recovery request");
            return null;
        }
        ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "stream-checkpoint");
            t.setDaemon(true);
            return t;
        });
        Checkpointer c = new Checkpointer(timer);
        timer.scheduleWithFixedDelay(() -> c.write(queue, file), intervalMillis, intervalMillis, TimeUnit.MILLISECONDS);
        return c;
    }

    private void write(QueueStream stream, Path file) {
        if (stream.isClosed()) return;
        try {
            writeAtomically(file, StreamRequestJson.toJson(stream.currentRecoveryRequest()));
            written = true;
        } catch (Exception e) {
            log.warn("Could not write checkpoint {}", file, e);
        }
    }

    /** Writes into a temporary file first, so that a crash while writing never leaves a broken recovery file. */
    static void writeAtomically(Path file, String json) throws IOException {
        Path abs = file.toAbsolutePath();
        Path tmp = abs.resolveSibling(abs.getFileName() + ".tmp");
        Files.writeString(tmp, json);
        Files.move(tmp, abs, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    public boolean written() {
        return written;
    }

    @Override
    public void close() {
        timer.shutdownNow();
        try {
            timer.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
