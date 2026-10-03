package com.here.naksha.cli.stream;

import naksha.base.AnyObject;
import naksha.base.Id;
import naksha.base.PlatformMap;
import naksha.model.IStorage;
import naksha.model.IStreamSession;
import naksha.model.NakshaContext;
import naksha.model.SessionOptions;
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
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class StreamCopyServiceTest {
    private final IStorage storage = mock(IStorage.class);
    private final SessionOptions options = SessionOptions.from(NakshaContext.currentContext().withAppId("stream-test"));

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

    private static void assertPerFeatureOrder(List<StreamTuple> written) {
        Map<Long, Long> last = new HashMap<>();
        for (StreamTuple t : written) {
            Long prev = last.put(t.id().number(), t.version());
            if (prev != null) assertTrue(prev < t.version(), "state of " + t.id() + " written out of order");
        }
    }

    @Test
    void copiesEverythingIntoAllTargetsInPerFeatureOrder() {
        List<StreamTuple> tuples = history();
        var source = new ListStreamSession(storage, options, tuples);
        var a = new RecordingTargetSession(storage, options);
        var b = new RecordingTargetSession(storage, options);

        var result = new StreamCopyService(8, s -> {}).copy(source.read(request(7)), List.<IStreamSession>of(a, b));

        assertInstanceOf(StreamCopyService.Done.class, result);
        assertEquals(tuples.size(), ((StreamCopyService.Done) result).tuples());
        assertEquals(tuples.size(), a.written.size());
        assertEquals(tuples.size(), b.written.size());
        assertPerFeatureOrder(a.written);
        assertPerFeatureOrder(b.written);
    }

    @Test
    void failureReturnsRecoveryRequestAndResumeCompletesTheCopy() {
        List<StreamTuple> tuples = history();
        var source = new ListStreamSession(storage, options, tuples);
        var target = new RecordingTargetSession(storage, options);
        AtomicBoolean failedOnce = new AtomicBoolean();
        long poison = tuples.get(tuples.size() / 2).version();
        target.failWhen = c -> List.of(c.tuples()).stream().anyMatch(t -> t.version() == poison) && failedOnce.compareAndSet(false, true);

        var first = new StreamCopyService(4, s -> {}).copy(source.read(request(5)), List.<IStreamSession>of(target));

        var aborted = assertInstanceOf(StreamCopyService.Aborted.class, first);
        assertNotNull(aborted.recoveryRequest());
        String json = StreamRequestJson.toJson(aborted.recoveryRequest());
        StreamRequest resumed = StreamRequestJson.fromJson(json);
        assertEquals(new Id("collection"), resumed.collectionId());

        var second = new StreamCopyService(4, s -> {}).copy(source.read(resumed), List.<IStreamSession>of(target));

        assertInstanceOf(StreamCopyService.Done.class, second);
        Set<Long> versions = new HashSet<>();
        for (StreamTuple t : target.written) versions.add(t.version());
        assertEquals(tuples.size(), versions.size(), "at-least-once: every state must be written after resume");
    }
}
