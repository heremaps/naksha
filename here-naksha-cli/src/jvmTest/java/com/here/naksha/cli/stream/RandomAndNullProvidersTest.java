package com.here.naksha.cli.stream;

import naksha.base.NakshaException;
import naksha.base.Platform;
import naksha.model.IStorageConfigBuilder;
import naksha.model.IStorageProvider;
import naksha.model.IStreamSession;
import naksha.model.NakshaContext;
import naksha.model.SessionOptions;
import naksha.model.objects.NakshaFeature;
import naksha.model.streaming.StreamRequest;
import naksha.model.streaming.StreamRequestBuilder;
import naksha.model.streaming.StreamTuple;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class RandomAndNullProvidersTest {
    private final SessionOptions options = SessionOptions.from(NakshaContext.currentContext().withAppId("stream-test"));
    private final StorageProviders providers = StorageProviders.load();

    private static String config(int count, int states) {
        return "{\"count\":" + count + ",\"statesPerFeature\":" + states + ",\"idsPrefix\":\"f\",\"tileIds\":[\"120210233222\"]}";
    }

    private IStreamSession session(String provider, String config) {
        return providers.get(provider).getStorage(config).newStreamSession(options);
    }

    private static StreamRequest request(int chunkSize, boolean headOnly) {
        return new StreamRequestBuilder("random", "default", "default")
                .withChunkSize(chunkSize).withQueryHistory(!headOnly).build();
    }

    private static SyncService service() {
        return new SyncService(1000, r -> {}, s -> {});
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
        assertEquals(Set.of("null", "random"), providers.all().stream().map(IStorageProvider::getName).collect(Collectors.toSet()));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> providers.get("v9"));
        assertTrue(e.getMessage().contains("null, random"), e.getMessage());
    }

    @Test
    void builtInProvidersNeedNoConfiguration() {
        for (IStorageProvider p : providers.all()) {
            IStorageConfigBuilder builder = p.newConfigBuilder();
            assertTrue(builder.isDone(), p.getName());
            assertEquals("{}", builder.build());
        }
    }

    @Test
    void sameConfigurationReturnsTheSameStorage() {
        IStorageProvider random = providers.get("random");
        assertSame(random.getStorage(config(10, 2)), random.getStorage(config(10, 2)));
        assertNotSame(random.getStorage(config(10, 2)), random.getStorage(config(11, 2)));
    }

    @Test
    void invalidRandomConfigurationIsReported() {
        NakshaException e = assertThrows(NakshaException.class, () -> providers.get("random").getStorage("{\"count\":\"lots\"}"));
        assertTrue(e.getMessage().contains("count must be a number"), e.getMessage());
    }

    @Test
    void randomDeliversEveryStateOfEveryFeatureInOrder() {
        RecordingTargetSession target = new RecordingTargetSession(providers.get("null").getStorage("{}"), options);

        var result = service().sync(session("random", config(40, 3)).read(request(7, false)), List.<IStreamSession>of(target));

        assertInstanceOf(SyncService.Done.class, result);
        assertEquals(120, target.written.size());
        assertEquals(120, pairs(target.written).size());
        Map<String, Integer> last = new HashMap<>();
        for (StreamTuple t : target.written) {
            Integer prev = last.put(t.id().text(), state(t));
            if (prev != null) assertTrue(prev < state(t), "states of " + t.id() + " out of order");
        }
    }

    @Test
    void headOnlyDeliversOneStatePerFeature() {
        RecordingTargetSession target = new RecordingTargetSession(providers.get("null").getStorage("{}"), options);

        service().sync(session("random", config(40, 3)).read(request(7, true)), List.<IStreamSession>of(target));

        assertEquals(40, target.written.size());
        assertTrue(target.written.stream().allMatch(t -> state(t) == 0));
    }

    @Test
    void randomIntoNullAcknowledgesEverything() {
        var result = service().sync(session("random", config(500, 3)).read(request(64, false)), List.of(session("null", "{}")));

        assertEquals(1500, assertInstanceOf(SyncService.Done.class, result).tuples());
    }

    @Test
    void abortedRandomCopyContinuesWithoutGaps() {
        String cfg = config(60, 3);
        RecordingTargetSession target = new RecordingTargetSession(providers.get("null").getStorage("{}"), options);
        AtomicBoolean failedOnce = new AtomicBoolean();
        target.failWhen = c -> List.of(c.tuples()).stream().anyMatch(t -> state(t) == 1) && failedOnce.compareAndSet(false, true);

        var first = service().sync(session("random", cfg).read(request(9, false)), List.<IStreamSession>of(target));
        var aborted = assertInstanceOf(SyncService.Aborted.class, first);
        assertNotNull(aborted.recoveryRequest());

        StreamRequest resumed = StreamRequestJson.fromJson(StreamRequestJson.toJson(aborted.recoveryRequest()));
        var second = service().sync(session("random", cfg).read(resumed), List.<IStreamSession>of(target));

        assertInstanceOf(SyncService.Done.class, second);
        assertEquals(180, pairs(target.written).size(), "every state of every feature must be written");
    }
}
