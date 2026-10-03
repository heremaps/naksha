package com.here.naksha.cli.stream;

import naksha.base.NakshaError;
import naksha.model.IStorage;
import naksha.model.SessionOptions;
import naksha.model.streaming.AbstractStreamSession;
import naksha.model.streaming.StreamChunk;
import naksha.model.streaming.StreamTuple;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

/** Records written tuples in write order; can be told to fail chunks. */
final class RecordingTargetSession extends AbstractStreamSession {
    final List<StreamTuple> written = new CopyOnWriteArrayList<>();
    volatile Predicate<StreamChunk> failWhen = c -> false;

    RecordingTargetSession(IStorage storage, SessionOptions options) {
        super(storage, options);
    }

    @Override
    public boolean getMayWrite() {
        return true;
    }

    @Override
    public boolean write(@NotNull StreamChunk chunk) {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(3));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (failWhen.test(chunk)) {
            chunk.fail(new NakshaError(NakshaError.EXCEPTION, "simulated write failure", null));
            return false;
        }
        written.addAll(List.of(chunk.tuples()));
        chunk.acknowledge();
        return true;
    }
}
