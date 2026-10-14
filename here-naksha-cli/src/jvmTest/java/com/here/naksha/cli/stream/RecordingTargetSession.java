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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/** Queues the chunks, writers record the tuples in write order and acknowledge asynchronously; can be told to fail chunks. */
class RecordingTargetSession extends AbstractStreamSession {
    final List<StreamTuple> written = new CopyOnWriteArrayList<>();
    volatile Predicate<StreamChunk> failWhen = c -> false;

    private final Semaphore queue = new Semaphore(8);
    private final ExecutorService writers = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "recording-writer");
        t.setDaemon(true);
        return t;
    });

    RecordingTargetSession(IStorage storage, SessionOptions options) {
        super(storage, options);
    }

    @Override
    public boolean getMayWrite() {
        return true;
    }

    @Override
    public void write(@NotNull StreamChunk chunk) {
        queue.acquireUninterruptibly();
        writers.execute(() -> {
            try {
                Thread.sleep(ThreadLocalRandom.current().nextInt(3));
                if (failWhen.test(chunk)) {
                    chunk.fail(new NakshaError(NakshaError.EXCEPTION, "simulated write failure", null));
                } else {
                    written.addAll(List.of(chunk.tuples()));
                    chunk.acknowledge();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                chunk.fail(new NakshaError(NakshaError.EXCEPTION, "interrupted", e));
            } finally {
                queue.release();
            }
        });
    }

    @Override
    public void close() {
        writers.shutdown();
        try {
            writers.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        super.close();
    }
}
