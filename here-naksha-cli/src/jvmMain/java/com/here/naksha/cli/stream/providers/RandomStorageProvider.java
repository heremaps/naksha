package com.here.naksha.cli.stream.providers;

import com.here.naksha.cli.storages.GeneratingStorageConfigProperties;
import com.here.naksha.cli.storages.GeneratingStorageService;
import com.here.naksha.cli.utils.JsonParser;
import com.here.naksha.cli.utils.JsonParserException;
import naksha.base.Id;
import naksha.base.JvmBoxingUtil;
import naksha.base.NakshaError;
import naksha.base.NakshaException;
import naksha.base.PlatformMap;
import naksha.base.StringList;
import naksha.model.IStorage;
import naksha.model.IStorageConfigBuilder;
import naksha.model.IStorageProvider;
import naksha.model.IStreamSession;
import naksha.model.SessionOptions;
import naksha.model.objects.NakshaFeature;
import naksha.model.objects.NakshaStorage;
import naksha.model.streaming.AbstractStreamSession;
import naksha.model.streaming.QueueStream;
import naksha.model.streaming.Stream;
import naksha.model.streaming.StreamChunk;
import naksha.model.streaming.StreamRequest;
import naksha.model.streaming.StreamTuple;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Ids are {@code {idsPrefix}{index}}, so a resumed stream continues with the same features. */
public final class RandomStorageProvider implements IStorageProvider {
    static final String CURSOR = "randomCursor";
    static final int DEFAULT_COUNT = 1000;
    static final int DEFAULT_STATES = 3;
    static final String DEFAULT_TILE = "120210233222";

    @Override
    public @NotNull String getName() {
        return "random";
    }

    @Override
    public @NotNull String getDescription() {
        return "A source that generates random features, with multiple states per feature.";
    }

    @Override
    public @NotNull IStorageConfigBuilder newConfigBuilder() {
        return new NoQuestions();
    }

    @Override
    public @NotNull IStorage getStorage(@NotNull String config) {
        return StreamOnlyStorage.use(getName(), RandomStorage.class, config);
    }

    record Config(int count, int statesPerFeature, String idsPrefix, List<String> tileIds, NakshaFeature template) {
        static Config from(@NotNull NakshaStorage storage) {
            try {
                GeneratingStorageConfigProperties p = JvmBoxingUtil.box(storage.getProperties(), GeneratingStorageConfigProperties.class);
                int count = number(p, "count", DEFAULT_COUNT);
                int states = number(p, "statesPerFeature", DEFAULT_STATES);
                String prefix = p != null && p.getIdsPrefix() != null ? p.getIdsPrefix() : "gen";
                List<String> tiles;
                if (p != null && p.getTileIdsCsvFilePath() != null) {
                    tiles = Files.readAllLines(Path.of(p.getTileIdsCsvFilePath())).stream().filter(s -> !s.isBlank()).toList();
                } else {
                    StringList list = p != null ? p.getTileIds() : null;
                    tiles = list != null ? List.copyOf(list) : List.of(DEFAULT_TILE);
                }
                NakshaFeature template = p != null && p.getFeatureTemplateFilePath() != null
                        ? new JsonParser().parse(Files.readString(Path.of(p.getFeatureTemplateFilePath())), NakshaFeature.class)
                        : new NakshaFeature();
                if (count <= 0 || states <= 0 || tiles.isEmpty()) {
                    throw new NakshaException(NakshaError.ILLEGAL_ARGUMENT, "count and statesPerFeature must be > 0, and at least one tile is needed");
                }
                return new Config(count, states, prefix, tiles, template);
            } catch (JsonParserException | IOException | ClassCastException e) {
                throw new NakshaException(NakshaError.ILLEGAL_ARGUMENT, "Invalid random configuration: " + e.getMessage());
            }
        }

        private static int number(GeneratingStorageConfigProperties p, String key, int defaultValue) {
            Object value = p != null ? p.getRaw(key) : null;
            if (value == null) return defaultValue;
            if (value instanceof Number n) return n.intValue();
            throw new NakshaException(NakshaError.ILLEGAL_ARGUMENT, "Invalid random configuration: " + key + " must be a number");
        }
    }

    public static final class RandomStorage extends StreamOnlyStorage {
        private volatile Config config;

        @Override
        protected void init(@NotNull NakshaStorage storage) {
            config = Config.from(storage);
        }

        @Override
        public @NotNull IStreamSession newStreamSession(@Nullable SessionOptions options) {
            return new RandomStreamSession(this, config, orDefault(options));
        }
    }

    static final class RandomStreamSession extends AbstractStreamSession {
        private final Config config;

        RandomStreamSession(IStorage storage, Config config, SessionOptions options) {
            super(storage, options);
            this.config = config;
        }

        @Override
        public boolean getMayRead() {
            return true;
        }

        @Override
        public @NotNull Stream read(@NotNull StreamRequest request) {
            return new RandomStream(this, request, config);
        }
    }

    static final class RandomStream extends QueueStream {
        private final Config config;
        private final int states;
        private final long start;
        private final GeneratingStorageService generator = new GeneratingStorageService();

        RandomStream(RandomStreamSession session, StreamRequest request, Config config) {
            super(session, request, session.getOptions(), request.sequential() ? 1 : 16);
            this.config = config;
            this.states = request.queryHistory() ? config.statesPerFeature() : 1;
            this.start = request.getRaw(CURSOR) instanceof Number n ? n.longValue() : 0L;
            start();
        }

        @Override
        protected long getStartPosition() {
            return start;
        }

        @Override
        public long getEstimatedTuples() {
            return (long) config.count() * states;
        }

        // Position p addresses state p / count of feature p % count, so chunks are emitted state by state.
        @Override
        protected void produce(@NotNull Sink sink) {
            long n = config.count();
            long end = n * states;
            int chunkSize = Math.max(1, request().chunkSize());
            long p = start;
            while (p < end) {
                int state = (int) (p / n);
                int first = (int) (p % n);
                int len = (int) Math.min(chunkSize, n - first);
                List<NakshaFeature> features = generator.generateFeatures(len, config.tileIds(), config.idsPrefix(), config.template());
                StreamTuple[] tuples = new StreamTuple[len];
                for (int k = 0; k < len; k++) {
                    NakshaFeature f = features.get(k);
                    String id = config.idsPrefix() + (first + k);
                    f.setId(id);
                    f.getProperties().put("state", state);
                    tuples[k] = new StreamTuple(new Id(id), (PlatformMap) f.platformObject(), null, -1L, -1L, null);
                }
                if (!sink.emit(new StreamChunk(this, tuples), p, p + len)) return;
                p += len;
            }
        }

        @Override
        protected @NotNull StreamRequest recoveryRequestAt(long position) {
            StreamRequest r = new StreamRequest();
            r.putAll(request());
            r.put(CURSOR, position);
            return r;
        }
    }
}
