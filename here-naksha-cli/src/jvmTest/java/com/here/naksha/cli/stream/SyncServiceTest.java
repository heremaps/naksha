package com.here.naksha.cli.stream;

import naksha.base.AnyObject;
import naksha.base.Id;
import naksha.base.NakshaError;
import naksha.base.PlatformMap;
import naksha.model.IStorage;
import naksha.model.IStreamSession;
import naksha.model.NakshaContext;
import naksha.model.SessionOptions;
import naksha.model.streaming.Stream;
import naksha.model.streaming.StreamChunk;
import naksha.model.streaming.StreamRequest;
import naksha.model.streaming.StreamRequestBuilder;
import naksha.model.streaming.StreamTuple;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class SyncServiceTest {
    private final IStorage storage = mock(IStorage.class);
    private final SessionOptions options = SessionOptions.from(NakshaContext.currentContext().withAppId("stream-test"));
    private final List<StreamRequest> saved = new CopyOnWriteArrayList<>();

    /** 50 features with 1..4 states each, versions interleaved like a real history. */
    private static List<StreamTuple> history() {
        List<StreamTuple> tuples = new ArrayList<>();
        long version = 1;
        for (int round = 0; round < 4; round++) {
            for (int f = 0; f < 50; f++) {
                if (f % 4 < round) continue;
                AnyObject feature = new AnyObject();
                feature.put("id", "f" + f);
                tuples.add(new StreamTuple(new Id("f" + f), (PlatformMap) feature.platformObject(), null, -1L, version++, null));
            }
        }
        return tuples;
    }

    private static StreamRequest request(int chunkSize) {
        return new StreamRequestBuilder("db", "catalog", "collection").withChunkSize(chunkSize).build();
    }

    private SyncService service(long saveIntervalMillis) {
        return new SyncService(saveIntervalMillis, saved::add, s -> {});
    }

    private static void assertPerFeatureOrder(List<StreamTuple> written) {
        Map<Long, Long> last = new HashMap<>();
        for (StreamTuple t : written) {
            Long prev = last.put(t.id().number(), t.version());
            if (prev != null) assertTrue(prev < t.version(), "state of " + t.id() + " written out of order");
        }
    }

    private static long cursor(StreamRequest r) {
        return ((Number) r.getRaw("cursor")).longValue();
    }

    @Test
    void copiesEverythingIntoAllTargetsInPerFeatureOrder() {
        List<StreamTuple> tuples = history();
        var source = new ListStreamSession(storage, options, tuples);
        var a = new RecordingTargetSession(storage, options);
        var b = new RecordingTargetSession(storage, options);

        var result = service(1000).sync(source.read(request(7)), List.<IStreamSession>of(a, b));

        var done = assertInstanceOf(SyncService.Done.class, result);
        assertEquals(tuples.size(), done.tuples());
        assertEquals(tuples.size(), a.written.size());
        assertEquals(tuples.size(), b.written.size());
        assertPerFeatureOrder(a.written);
        assertPerFeatureOrder(b.written);
        assertEquals(tuples.size(), cursor(done.recoveryRequest()), "the final state continues behind the last tuple");
        assertSame(done.recoveryRequest(), saved.getLast());
    }

    @Test
    void recoveryRequestIsTheSameInstanceUntilTheStreamMoves() {
        Stream stream = new ListStreamSession(storage, options, history()).read(request(5));
        StreamRequest first = stream.getRecoveryRequest();
        assertSame(first, stream.getRecoveryRequest());

        StreamChunk chunk = stream.next();
        chunk.setTargets(1);
        assertSame(first, stream.getRecoveryRequest(), "an outstanding chunk does not move the recovery point");

        chunk.acknowledge();
        StreamRequest moved = stream.getRecoveryRequest();
        assertNotSame(first, moved);
        assertTrue(cursor(moved) > cursor(first));
        assertSame(moved, stream.closeForRecovery());
    }

    @Test
    void savesTheStateWhileRunning() {
        var source = new ListStreamSession(storage, options, history());
        var target = new RecordingTargetSession(storage, options);

        assertInstanceOf(SyncService.Done.class, service(0).sync(source.read(request(5)), List.<IStreamSession>of(target)));

        assertTrue(saved.size() > 2, "saved " + saved.size() + " times");
        for (int i = 1; i < saved.size(); i++) assertTrue(cursor(saved.get(i - 1)) <= cursor(saved.get(i)));
    }

    @Test
    void failureSavesTheRecoveryRequestAndContinueCompletesTheCopy() {
        List<StreamTuple> tuples = history();
        var source = new ListStreamSession(storage, options, tuples);
        var target = new RecordingTargetSession(storage, options);
        AtomicBoolean failedOnce = new AtomicBoolean();
        long poison = tuples.get(tuples.size() / 2).version();
        target.failWhen = c -> List.of(c.tuples()).stream().anyMatch(t -> t.version() == poison) && failedOnce.compareAndSet(false, true);

        var first = service(1000).sync(source.read(request(5)), List.<IStreamSession>of(target));

        var aborted = assertInstanceOf(SyncService.Aborted.class, first);
        assertEquals(NakshaError.EXCEPTION, aborted.error().getCode());
        assertNotNull(aborted.recoveryRequest());
        assertSame(aborted.recoveryRequest(), saved.getLast());

        StreamRequest resumed = StreamRequestJson.fromJson(StreamRequestJson.toJson(aborted.recoveryRequest()));
        var second = service(1000).sync(source.read(resumed), List.<IStreamSession>of(target));

        assertInstanceOf(SyncService.Done.class, second);
        Set<Long> versions = new HashSet<>();
        for (StreamTuple t : target.written) versions.add(t.version());
        assertEquals(tuples.size(), versions.size(), "at-least-once: every state must be written after continue");
    }

    @Test
    void streamClosedFromOutsideIsReportedAsAborted() {
        List<StreamTuple> tuples = history();
        var source = new ListStreamSession(storage, options, tuples);
        var target = new RecordingTargetSession(storage, options);
        AtomicBoolean closed = new AtomicBoolean();

        // Like the Ctrl-C hook: close the stream for recovery while the sync is running.
        var result = new SyncService(1000, saved::add, s -> {
            if (closed.compareAndSet(false, true)) s.closeForRecovery();
        }).sync(source.read(request(5)), List.<IStreamSession>of(target));

        var aborted = assertInstanceOf(SyncService.Aborted.class, result);
        assertEquals(NakshaError.CLOSED, aborted.error().getCode());
        assertTrue(target.written.size() < tuples.size());
    }
}
