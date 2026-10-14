package com.here.naksha.cli.stream;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Keeps every entity in {@code {dir}/{name}.json}, only readable by the user, because storage configurations contain credentials. */
public final class JsonFileStore<T> implements Store<T> {
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");

    private final Path dir;
    private final Function<T, Map<String, Object>> toJson;
    private final BiFunction<String, Map<String, Object>, T> fromJson;

    public JsonFileStore(@NotNull Path dir, @NotNull Function<T, Map<String, Object>> toJson, @NotNull BiFunction<String, Map<String, Object>, T> fromJson) {
        this.dir = dir;
        this.toJson = toJson;
        this.fromJson = fromJson;
    }

    public static void requireValidName(@NotNull String name) {
        if (!NAME.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid name '" + name + "', use letters, digits, '.', '_' and '-'");
        }
    }

    private Path file(String name) {
        requireValidName(name);
        return dir.resolve(name + ".json");
    }

    @Override
    public @NotNull List<String> names() {
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> files = Files.list(dir)) {
            return files.map(p -> p.getFileName().toString())
                    .filter(f -> f.endsWith(".json"))
                    .map(f -> f.substring(0, f.length() - ".json".length()))
                    .filter(n -> NAME.matcher(n).matches())
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public @NotNull Optional<T> get(@NotNull String name) {
        Path f = file(name);
        if (!Files.isRegularFile(f)) return Optional.empty();
        try {
            return Optional.of(fromJson.apply(name, Json.parseObject(Files.readString(f))));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid file " + f + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void save(@NotNull String name, @NotNull T value) {
        Path f = file(name);
        try {
            Files.createDirectories(dir);
            Path tmp = dir.resolve(name + ".json.tmp");
            Files.writeString(tmp, Json.write(toJson.apply(value)));
            try {
                Files.setPosixFilePermissions(tmp, PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException ignored) {
                // not a POSIX file system
            }
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public boolean delete(@NotNull String name) {
        try {
            return Files.deleteIfExists(file(name));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
