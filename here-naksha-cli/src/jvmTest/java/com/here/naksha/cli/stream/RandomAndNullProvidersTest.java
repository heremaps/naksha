package com.here.naksha.cli.stream;

import naksha.base.Platform;
import naksha.model.IStreamSession;
import naksha.model.NakshaContext;
import naksha.model.SessionOptions;
import naksha.model.objects.NakshaFeature;
import naksha.model.streaming.StreamRequest;
import naksha.model.streaming.StreamRequestBuilder;
import naksha.model.streaming.StreamTuple;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class RandomAndNullProvidersTest {
    private final SessionOptions options = SessionOptions.from(NakshaContext.currentContext().withAppId("stream-test"));
    private final StreamProviders providers = StreamProviders.load();

    @TempDir
    Path dir;

    private Path config(int count, int states) throws Exception {
        Path p = dir.resolve("random.json");
        Files.writeString(p, "{\"count\":" + count + ",\"statesPerFeature\":" + states + ",\"idsPrefix\":\"f\",\"tileIds\":[\"120210233222\"]}");
        return p;
    }

    private static StreamRequest request(int chunkSize, boolean headOnly) {
        return new StreamRequestBuilder("random", "default", "default")
                .withChunkSize(chunkSize).withQueryHistory(!headOnly).withIgnoreTransactions(headOnly).build();
    }

    private static int state(StreamTuple t) {
        NakshaFeature f = Platform.javaProxy(t.feature(), NakshaFeature.class);
        return ((Number) f.getProperties().get("state")).intValue();
    }

    private static Set<String> pairs(List<StreamTuple> written) {
        Set<String> s = new HashSet<>();
        for (StreamTuple t : written) s.add(t.id().text() + "#" + state(t));
        return s;
    }

    @Test
    void serviceLoaderFindsBuiltInProviders() {
        assertEquals(Set.of("null", "random"), providers.all().stream().map(StreamProvider::name).collect(java.util.stream.Collectors.toSet()));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> providers.get("v9"));
        assertTrue(e.getMessage().contains("null, random"), e.getMessage());
    }

    @Test
    void randomDeliversEveryStateOfEveryFeatureInOrder() throws Exception {
        IStreamSession source = providers.get("random").open(config(40, 3), options);
        RecordingTargetSession target = new RecordingTargetSession(null, options);

        var result = new StreamCopyService(8, s -> {}).copy(source.read(request(7, false)), List.<IStreamSession>of(target));

        assertInstanceOf(StreamCopyService.Done.class, result);
        assertEquals(120, target.written.size());
        assertEquals(120, pairs(target.written).size());
        Map<String, Integer> last = new HashMap<>();
        for (StreamTuple t : target.written) {
            Integer prev = last.put(t.id().text(), state(t));
            if (prev != null) assertTrue(prev < state(t), "states of " + t.id() + " out of order");
        }
    }

    @Test
    void headOnlyDeliversOneStatePerFeature() throws Exception {
        IStreamSession source = providers.get("random").open(config(40, 3), options);
        RecordingTargetSession target = new RecordingTargetSession(null, options);

        new StreamCopyService(8, s -> {}).copy(source.read(request(7, true)), List.<IStreamSession>of(target));

        assertEquals(40, target.written.size());
        assertTrue(target.written.stream().allMatch(t -> state(t) == 0));
    }

    @Test
    void randomIntoNullAcknowledgesEverything() throws Exception {
        IStreamSession source = providers.get("random").open(config(500, 3), options);
        IStreamSession sink = providers.get("null").open(null, options);

        var result = new StreamCopyService(16, s -> {}).copy(source.read(request(64, false)), List.of(sink));

        assertEquals(1500, assertInstanceOf(StreamCopyService.Done.class, result).tuples());
    }

    @Test
    void abortedRandomCopyResumesWithoutGaps() throws Exception {
        Path cfg = config(60, 3);
        RecordingTargetSession target = new RecordingTargetSession(null, options);
        AtomicBoolean failedOnce = new AtomicBoolean();
        target.failWhen = c -> List.of(c.tuples()).stream().anyMatch(t -> state(t) == 1) && failedOnce.compareAndSet(false, true);

        var first = new StreamCopyService(4, s -> {})
                .copy(providers.get("random").open(cfg, options).read(request(9, false)), List.<IStreamSession>of(target));
        var aborted = assertInstanceOf(StreamCopyService.Aborted.class, first);
        assertNotNull(aborted.recoveryRequest());

        StreamRequest resumed = StreamRequestJson.fromJson(StreamRequestJson.toJson(aborted.recoveryRequest()));
        var second = new StreamCopyService(4, s -> {})
                .copy(providers.get("random").open(cfg, options).read(resumed), List.<IStreamSession>of(target));

        assertInstanceOf(StreamCopyService.Done.class, second);
        assertEquals(180, pairs(target.written).size(), "every state of every feature must be written");
    }
}
