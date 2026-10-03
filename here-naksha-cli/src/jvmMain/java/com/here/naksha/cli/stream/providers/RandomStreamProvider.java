package com.here.naksha.cli.stream.providers;

import com.here.naksha.cli.storages.GeneratingStorageConfigProperties;
import com.here.naksha.cli.storages.GeneratingStorageService;
import com.here.naksha.cli.stream.StreamProvider;
import com.here.naksha.cli.utils.JsonParser;
import com.here.naksha.cli.utils.JsonParserException;
import naksha.base.Id;
import naksha.base.NakshaError;
import naksha.base.NakshaException;
import naksha.base.PlatformMap;
import naksha.base.StringList;
import naksha.model.IStreamSession;
import naksha.model.SessionOptions;
import naksha.model.objects.NakshaFeature;
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
public final class RandomStreamProvider implements StreamProvider {
    static final String CURSOR = "randomCursor";
    static final int DEFAULT_COUNT = 1000;
    static final int DEFAULT_STATES = 3;
    static final String DEFAULT_TILE = "120210233222";

    @Override
    public @NotNull String name() {
        return "random";
    }

    @Override
    public @NotNull String description() {
        return "A source that generates random features, with multiple states per feature.";
    }

    @Override
    public @NotNull IStreamSession open(@Nullable Path config, @NotNull SessionOptions options) {
        return new RandomStreamSession(Config.load(config), options);
    }

    record Config(int count, int statesPerFeature, String idsPrefix, List<String> tileIds, NakshaFeature template) {
        static Config load(@Nullable Path path) {
            if (path == null) {
                return new Config(DEFAULT_COUNT, DEFAULT_STATES, "gen", List.of(DEFAULT_TILE), new NakshaFeature());
            }
            try {
                JsonParser parser = new JsonParser();
                GeneratingStorageConfigProperties p = parser.readAndParse(path, GeneratingStorageConfigProperties.class);
                int count = p.getCount() != null ? p.getCount() : DEFAULT_COUNT;
                int states = p.getRaw("statesPerFeature") instanceof Number n ? n.intValue() : DEFAULT_STATES;
                String prefix = p.getIdsPrefix() != null ? p.getIdsPrefix() : "gen";
                List<String> tiles;
                if (p.getTileIdsCsvFilePath() != null) {
                    tiles = Files.readAllLines(Path.of(p.getTileIdsCsvFilePath())).stream().filter(s -> !s.isBlank()).toList();
                } else {
                    StringList list = p.getTileIds();
                    tiles = list != null ? List.copyOf(list) : List.of(DEFAULT_TILE);
                }
                NakshaFeature template = p.getFeatureTemplateFilePath() != null
                        ? parser.parse(Files.readString(Path.of(p.getFeatureTemplateFilePath())), NakshaFeature.class)
                        : new NakshaFeature();
                if (count <= 0 || states <= 0 || tiles.isEmpty()) {
                    throw new NakshaException(NakshaError.ILLEGAL_ARGUMENT, "count and statesPerFeature must be > 0, and at least one tile is needed");
                }
                return new Config(count, states, prefix, tiles, template);
            } catch (JsonParserException | IOException e) {
                throw new NakshaException(NakshaError.ILLEGAL_ARGUMENT, "Invalid random source configuration: " + e.getMessage());
            }
        }
    }

    static final class RandomStreamSession extends AbstractStreamSession {
        private final Config config;

        RandomStreamSession(Config config, SessionOptions options) {
            super(null, options);
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
