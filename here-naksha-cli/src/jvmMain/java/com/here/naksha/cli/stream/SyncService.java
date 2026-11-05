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

import java.util.List;
import java.util.function.Consumer;

/** The read loop of {@link Stream}: writes every chunk into all targets, which acknowledge it, and saves the recovery request when it moved. */
public final class SyncService {
    public sealed interface Result permits Done, Aborted {}

    public record Done(long tuples, @NotNull StreamRequest recoveryRequest) implements Result {}

    /** @param recoveryRequest the request to continue with, {@code null} if the stream is not recoverable. */
    public record Aborted(@NotNull NakshaError error, @Nullable StreamRequest recoveryRequest) implements Result {}

    private final long saveIntervalMillis;
    private final Consumer<StreamRequest> save;
    private final Consumer<Stream> progress;

    /**
     * @param saveIntervalMillis the minimal time between two saves while the stream runs.
     * @param save called with the recovery request to persist.
     * @param progress called after every chunk.
     */
    public SyncService(long saveIntervalMillis, @NotNull Consumer<StreamRequest> save, @NotNull Consumer<Stream> progress) {
        this.saveIntervalMillis = saveIntervalMillis;
        this.save = save;
        this.progress = progress;
    }

    public @NotNull Result sync(@NotNull Stream stream, @NotNull List<IStreamSession> targets) {
        if (targets.isEmpty()) throw new IllegalArgumentException("At least one target is required");
        try (stream) {
            StreamRequest saved = stream.getRecoveryRequest();
            long savedAt = System.currentTimeMillis();
            while (stream.hasNext()) {
                StreamChunk chunk = stream.next();
                chunk.setTargets(targets.size());
                for (IStreamSession target : targets) {
                    target.write(chunk);
                }
                StreamRequest latest = stream.getRecoveryRequest();
                long now = System.currentTimeMillis();
                if (latest != saved && now - savedAt >= saveIntervalMillis) {
                    save.accept(latest);
                    saved = latest;
                    savedAt = now;
                }
                progress.accept(stream);
            }
            // Closed from outside, for example by the Ctrl-C hook, which saves the state itself.
            if (stream.isClosed()) {
                return new Aborted(new NakshaError(NakshaError.CLOSED, "Stream was closed before all chunks were copied", null), null);
            }
        } catch (Exception e) {
            StreamException se = streamException(e);
            if (se != null) {
                save.accept(se.getRecoveryRequest());
                return new Aborted(se.getError(), se.getRecoveryRequest());
            }
            return new Aborted(e instanceof NakshaException ne ? ne.getError() : new NakshaError(NakshaError.EXCEPTION, String.valueOf(e.getMessage()), e), null);
        }
        StreamRequest last = stream.getRecoveryRequest();
        save.accept(last);
        return new Done(stream.getAcknowledgedTuples(), last);
    }

    private static @Nullable StreamException streamException(Throwable e) {
        if (e instanceof StreamException se) return se;
        for (Throwable s : e.getSuppressed()) {
            if (s instanceof StreamException se) return se;
        }
        return null;
    }
}
