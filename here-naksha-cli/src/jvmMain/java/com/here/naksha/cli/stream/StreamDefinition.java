package com.here.naksha.cli.stream;

import naksha.base.Id;
import naksha.base.Version;
import naksha.model.streaming.StreamRequest;
import naksha.model.streaming.StreamRequestBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** What to copy from where to where, and how far it got. */
public record StreamDefinition(
        @NotNull Endpoint source,
        @NotNull List<Endpoint> targets,
        @NotNull Options options,
        @NotNull State state
) {
    public record Endpoint(@NotNull String storage, @NotNull String catalog, @NotNull String collection) {
        @Override
        public String toString() {
            return storage + ":" + catalog + "/" + collection;
        }
    }

    public record Options(boolean queryHistory, boolean queryDeleted, boolean ignoreTransactions, int chunkSize) {}

    /** @param recoveryRequest the latest recovery request as JSON, or {@code null} if the stream never ran. */
    public record State(@NotNull String status, @Nullable String lastRun, long acknowledgedTuples, @Nullable String recoveryRequest) {
        public static final State NEW = new State("New", null, 0L, null);
    }

    public @NotNull StreamDefinition withState(@NotNull State state) {
        return new StreamDefinition(source, targets, options, state);
    }

    /** A new request that reads the stream from the beginning. */
    public @NotNull StreamRequest newRequest() {
        return new StreamRequestBuilder(new Id(source.storage()), new Id(source.catalog()), new Id(source.collection()))
                .withMinVersion(0L)
                .withVersion(Version.HEAD.number)
                .withQueryHistory(options.queryHistory())
                .withQueryDeleted(options.queryDeleted())
                .withIgnoreTransactions(options.ignoreTransactions())
                .withChunkSize(options.chunkSize())
                .build();
    }

    static @NotNull Map<String, Object> toJson(@NotNull StreamDefinition d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("source", endpoint(d.source()));
        List<Object> targets = new ArrayList<>();
        for (Endpoint t : d.targets()) targets.add(endpoint(t));
        m.put("targets", targets);
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("queryHistory", d.options().queryHistory());
        request.put("queryDeleted", d.options().queryDeleted());
        request.put("ignoreTransactions", d.options().ignoreTransactions());
        request.put("chunkSize", d.options().chunkSize());
        m.put("request", request);
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("status", d.state().status());
        state.put("lastRun", d.state().lastRun());
        state.put("acknowledgedTuples", d.state().acknowledgedTuples());
        state.put("recoveryRequest", d.state().recoveryRequest() == null ? null : Json.parseObject(d.state().recoveryRequest()));
        m.put("state", state);
        return m;
    }

    static @NotNull StreamDefinition fromJson(@NotNull String name, @NotNull Map<String, Object> m) {
        Endpoint source = endpoint(map(m.get("source"), "source"));
        List<Endpoint> targets = new ArrayList<>();
        if (m.get("targets") instanceof List<?> list) {
            for (Object t : list) targets.add(endpoint(map(t, "targets")));
        }
        if (targets.isEmpty()) throw new IllegalArgumentException("At least one target is required");
        Map<String, Object> request = m.get("request") == null ? Map.of() : map(m.get("request"), "request");
        Options options = new Options(
                bool(request.get("queryHistory"), true),
                bool(request.get("queryDeleted"), true),
                bool(request.get("ignoreTransactions"), false),
                request.get("chunkSize") instanceof Number n ? n.intValue() : 1000);
        State state = State.NEW;
        if (m.get("state") != null) {
            Map<String, Object> s = map(m.get("state"), "state");
            Object recovery = s.get("recoveryRequest");
            state = new State(
                    s.get("status") instanceof String st ? st : "New",
                    s.get("lastRun") instanceof String lr ? lr : null,
                    s.get("acknowledgedTuples") instanceof Number n ? n.longValue() : 0L,
                    recovery == null ? null : Json.write(recovery).trim());
        }
        return new StreamDefinition(source, targets, options, state);
    }

    private static Map<String, Object> endpoint(Endpoint e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("storage", e.storage());
        m.put("catalog", e.catalog());
        m.put("collection", e.collection());
        return m;
    }

    private static Endpoint endpoint(Map<String, Object> m) {
        return new Endpoint(string(m, "storage"), string(m, "catalog"), string(m, "collection"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object o, String what) {
        if (o instanceof Map<?, ?> m) return (Map<String, Object>) m;
        throw new IllegalArgumentException("Missing " + what);
    }

    private static String string(Map<String, Object> m, String key) {
        if (m.get(key) instanceof String s && !s.isBlank()) return s;
        throw new IllegalArgumentException("Missing " + key);
    }

    private static boolean bool(Object o, boolean defaultValue) {
        return o instanceof Boolean b ? b : defaultValue;
    }
}
