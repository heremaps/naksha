package com.here.naksha.cli.copy.resolvers;

import com.here.naksha.cli.storages.GeneratingStorageConfig;
import com.here.naksha.cli.storages.GeneratingStorageConfigProperties;
import naksha.base.StringList;
import org.apache.logging.log4j.util.Strings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

final class GeneratingStorageUriResolver implements StorageUriResolver {
    private static final String EXPECTED_URI_FORMAT = "gen://{count}[:{idsPrefix}]?tileIds={tileId1}[,{tileId2},...]";
    private final Pattern quadKeyPattern = Pattern.compile("[0123]+");

    @Override
    public @NotNull GeneratingStorageConfig resolve(@NotNull URI uri) {
        String rawAuthority = uri.getAuthority();
        Authority authority = parseAuthority(rawAuthority, uri);
        Map<String, String> queries = splitQuery(uri.getQuery());
        String rawTileIds = queries.get("tileIds");
        StringList tileIds = StringList.fromList(parseTileIds(rawTileIds, uri));
        GeneratingStorageConfigProperties properties = new GeneratingStorageConfigProperties()
                .withIdsPrefix(authority.idsPrefix)
                .withCount(authority.count)
                .withTileIds(tileIds);
        GeneratingStorageConfig config = new GeneratingStorageConfig();
        config.withProperties(properties);
        return config;
    }

    private record Authority(int count, @Nullable String idsPrefix) {
    }

    private Authority parseAuthority(String raw, URI uri) {
        if (raw == null) {
            throw new StorageUriResolverException(
                    "Provide correct authority! Received: null",
                    uri,
                    EXPECTED_URI_FORMAT
            );
        }
        List<String> params = List.of(raw.split(":"));
        if (params.size() > 2 || params.isEmpty()) {
            throw new StorageUriResolverException(
                    "Provide correct authority! Received: %s".formatted(raw),
                    uri,
                    EXPECTED_URI_FORMAT
            );
        }
        String idsPrefix = null;
        if (params.size() == 2) {
            idsPrefix = params.getLast();
        }
        String rawCount = params.getFirst();
        int count = parseCount(rawCount, uri);
        return new Authority(count, idsPrefix);
    }

    private int parseCount(String raw, URI uri) {
        try {
            raw = raw.replace("\\s", "");
            if (raw.isEmpty()) {
                throw new StorageUriResolverException(
                        "Count should be provided!",
                        uri,
                        EXPECTED_URI_FORMAT
                );
            }
            return requireCountIsPositiveInteger(Integer.parseInt(raw), uri);
        } catch (NumberFormatException _) {
            throw new StorageUriResolverException(
                    "Cannot parse count to integer! Received: %s".formatted(raw),
                    uri,
                    EXPECTED_URI_FORMAT
            );
        }
    }

    private int requireCountIsPositiveInteger(int count, URI uri) {
        if (count <= 0) {
            throw new StorageUriResolverException(
                    "Correct positive count must be provided! Received: %s".formatted(count),
                    uri,
                    EXPECTED_URI_FORMAT
            );
        }
        return count;
    }

    private List<String> parseTileIds(String raw, URI uri) {
        if (raw == null) {
            throw new StorageUriResolverException(
                    "tileIds should be provided!",
                    uri,
                    EXPECTED_URI_FORMAT
            );
        }
        raw = raw.replace("\\s", "");
        List<String> values = List.of(raw.split(","));
        requireTileIdsValuesAreQuadKeys(values, uri);
        return values;
    }

    private void requireTileIdsValuesAreQuadKeys(List<String> values, URI uri) {
        values.forEach(str -> {
            if (!quadKeyPattern.matcher(str).matches()) {
                throw new StorageUriResolverException(
                        "tileIds values must fulfill the regex %s. Received: %s".formatted(quadKeyPattern.pattern(), str),
                        uri,
                        EXPECTED_URI_FORMAT
                );
            }
        });
    }

    private Map<String, String> splitQuery(String query) {
        if (Strings.isBlank(query)) {
            return Collections.emptyMap();
        }
        return Arrays.stream(query.split("&"))
                .map(this::splitQueryParameter)
                .filter(entry -> entry.getValue() != null)
                .collect(
                        Collectors.toUnmodifiableMap(
                                AbstractMap.SimpleImmutableEntry::getKey, AbstractMap.SimpleImmutableEntry::getValue
                        )
                );
    }

    private AbstractMap.SimpleImmutableEntry<String, String> splitQueryParameter(String it) {
        int idx = it.indexOf("=");
        String key = idx > 0 ? it.substring(0, idx) : it;
        String value = idx > 0 && it.length() > idx + 1 ? it.substring(idx + 1) : null;
        return new AbstractMap.SimpleImmutableEntry<>(key, value);
    }
}
