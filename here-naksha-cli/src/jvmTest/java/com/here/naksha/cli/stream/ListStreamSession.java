package com.here.naksha.cli.stream;

import naksha.model.IStorage;
import naksha.model.SessionOptions;
import naksha.model.streaming.AbstractStreamSession;
import naksha.model.streaming.QueueStream;
import naksha.model.streaming.Stream;
import naksha.model.streaming.StreamChunk;
import naksha.model.streaming.StreamRequest;
import naksha.model.streaming.StreamTuple;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** In-memory source; the recovery cursor is the index into {@code tuples}. */
final class ListStreamSession extends AbstractStreamSession {
    private final List<StreamTuple> tuples;

    ListStreamSession(IStorage storage, SessionOptions options, List<StreamTuple> tuples) {
        super(storage, options);
        this.tuples = tuples;
    }

    @Override
    public boolean getMayRead() {
        return true;
    }

    @Override
    public @NotNull Stream read(@NotNull StreamRequest request) {
        return new ListStream(this, request, getOptions(), tuples);
    }

    static final class ListStream extends QueueStream {
        private final List<StreamTuple> tuples;
        private final long start;

        ListStream(ListStreamSession session, StreamRequest request, SessionOptions options, List<StreamTuple> tuples) {
            super(session, request, options, 4);
            this.tuples = tuples;
            this.start = request.getRaw("cursor") instanceof Number n ? n.longValue() : 0L;
            start();
        }

        @Override
        protected long getStartPosition() {
            return start;
        }

        @Override
        protected void produce(@NotNull Sink sink) {
            int size = request().chunkSize();
            List<StreamTuple> current = new ArrayList<>();
            Set<Long> ids = new HashSet<>();
            long first = start;
            for (int i = (int) start; i < tuples.size(); i++) {
                StreamTuple t = tuples.get(i);
                if (current.size() >= size || ids.contains(t.id().number())) {
                    if (!sink.emit(chunk(current), first, i)) return;
                    current = new ArrayList<>();
                    ids.clear();
                    first = i;
                }
                current.add(t);
                ids.add(t.id().number());
            }
            if (!current.isEmpty()) sink.emit(chunk(current), first, tuples.size());
        }

        private StreamChunk chunk(List<StreamTuple> list) {
            return new StreamChunk(this, list.toArray(StreamTuple[]::new));
        }

        @Override
        protected @NotNull StreamRequest recoveryRequestAt(long position) {
            StreamRequest r = new StreamRequest();
            r.putAll(request());
            r.put("cursor", position);
            return r;
        }
    }
}
