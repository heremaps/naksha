package com.here.naksha.cli.stream;

import naksha.base.Platform;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Plain Java maps and lists to and from JSON, written indented, so that the files stay readable. */
final class Json {
    private Json() {}

    static @NotNull Map<String, Object> parseObject(@NotNull String json) {
        if (toJava(Platform.fromJSON(json)) instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked") Map<String, Object> m = (Map<String, Object>) map;
            return m;
        }
        throw new IllegalArgumentException("Not a JSON object");
    }

    static @Nullable Object toJava(@Nullable Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) copy.put(String.valueOf(e.getKey()), toJava(e.getValue()));
            return copy;
        }
        if (value instanceof List<?> list) {
            List<Object> copy = new ArrayList<>(list.size());
            for (Object v : list) copy.add(toJava(v));
            return copy;
        }
        return value;
    }

    static @NotNull String write(@Nullable Object value) {
        StringBuilder sb = new StringBuilder();
        write(sb, value, "");
        return sb.append('\n').toString();
    }

    private static void write(StringBuilder sb, @Nullable Object value, String indent) {
        String inner = indent + "  ";
        if (value instanceof Map<?, ?> map) {
            if (map.isEmpty()) {
                sb.append("{}");
                return;
            }
            sb.append("{\n");
            int i = 0;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                sb.append(inner).append(Platform.toJSON(String.valueOf(e.getKey()))).append(": ");
                write(sb, e.getValue(), inner);
                sb.append(++i < map.size() ? ",\n" : "\n");
            }
            sb.append(indent).append('}');
        } else if (value instanceof List<?> list) {
            if (list.isEmpty()) {
                sb.append("[]");
                return;
            }
            sb.append("[\n");
            for (int i = 0; i < list.size(); i++) {
                sb.append(inner);
                write(sb, list.get(i), inner);
                sb.append(i + 1 < list.size() ? ",\n" : "\n");
            }
            sb.append(indent).append(']');
        } else {
            sb.append(Platform.toJSON(value));
        }
    }
}
